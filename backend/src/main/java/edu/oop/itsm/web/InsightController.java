package edu.oop.itsm.web;

import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.event.LiveEventHub;
import edu.oop.itsm.exception.NotFoundException;
import edu.oop.itsm.exception.UnauthorizedOperationException;
import edu.oop.itsm.intelligence.CategoryClassifier;
import edu.oop.itsm.intelligence.PriorityEngine;
import edu.oop.itsm.intelligence.SimilarityService;
import edu.oop.itsm.notification.NotificationService;
import edu.oop.itsm.repository.CategoryRepository;
import edu.oop.itsm.repository.ItServiceRepository;
import edu.oop.itsm.repository.SlaPolicyRepository;
import edu.oop.itsm.service.ReportService;
import edu.oop.itsm.web.dto.AdminDtos.CategoryView;
import edu.oop.itsm.web.dto.AdminDtos.ServiceView;
import edu.oop.itsm.web.dto.IncidentDtos.SimilarIncident;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Catalogue for forms, live "as you type" intelligence, dashboards, notifications and the SSE stream. */
@RestController
@RequestMapping("/api")
public class InsightController {

    private final ItServiceRepository services;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final CategoryClassifier classifier;
    private final PriorityEngine priorityEngine;
    private final SimilarityService similarity;
    private final ReportService reports;
    private final NotificationService notifications;
    private final LiveEventHub hub;

    public InsightController(ItServiceRepository services, CategoryRepository categories, SlaPolicyRepository slaPolicies,
                             CategoryClassifier classifier, PriorityEngine priorityEngine, SimilarityService similarity,
                             ReportService reports, NotificationService notifications, LiveEventHub hub) {
        this.services = services;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.classifier = classifier;
        this.priorityEngine = priorityEngine;
        this.similarity = similarity;
        this.reports = reports;
        this.notifications = notifications;
        this.hub = hub;
    }

    // ------------------------------------------------ catalogue

    public record Catalog(List<ServiceView> services, List<CategoryView> categories, List<String> impacts, List<String> urgencies) {}

    @GetMapping("/catalog")
    @Transactional(readOnly = true)
    public Catalog catalog() {
        return new Catalog(services.findByActiveTrueOrderByName().stream().map(ServiceView::of).toList(),
                categories.findByActiveTrueOrderByName().stream().map(CategoryView::of).toList(),
                List.of(Impact.values()).stream().map(Enum::name).toList(),
                List.of(Urgency.values()).stream().map(Enum::name).toList());
    }

    // ------------------------------------------------ live intelligence for the report form

    public record AnalyzeRequest(String title, String description, Long serviceId, Impact impact, Urgency urgency, Integer affectedUsers) {}

    public record SlaPreview(String policy, int responseMinutes, int resolutionMinutes) {}

    public record Analysis(List<CategoryClassifier.Suggestion> categories, PriorityEngine.Assessment priority, SlaPreview sla,
                           List<SimilarIncident> similar, List<SimilarIncident> knownFixes) {}

    @PostMapping("/insights/analyze")
    @Transactional(readOnly = true)
    public Analysis analyze(@RequestBody AnalyzeRequest req) {
        String title = req.title() == null ? "" : req.title();
        String desc = req.description() == null ? "" : req.description();
        ItService service = req.serviceId() == null ? null : services.findWithPolicyById(req.serviceId())
                .orElseThrow(() -> new NotFoundException("Service not found"));
        var cats = classifier.suggest(title, desc, service, categories.findByActiveTrueOrderByName());

        PriorityEngine.Assessment pa = null;
        SlaPreview sla = null;
        if (service != null && req.impact() != null && req.urgency() != null) {
            pa = priorityEngine.assess(req.impact(), req.urgency(), service.getCriticality(), req.affectedUsers() == null ? 1 : Math.max(1, req.affectedUsers()));
            SlaPolicy policy = service.getSlaPolicy() != null ? service.getSlaPolicy() : slaPolicies.findFirstByDefaultPolicyTrue().orElse(null);
            if (policy != null) {
                SlaTarget t = policy.targetFor(pa.priority());
                sla = new SlaPreview(policy.getName(), t.getResponseMinutes(), t.getResolutionMinutes());
            }
        }
        List<SimilarIncident> similar = List.of(), fixes = List.of();
        if (title.length() + desc.length() >= 8) {
            similar = similarity.findSimilarOpen(title, desc, req.serviceId(), -1L, 3).stream().map(InsightController::similar).toList();
            Long catId = cats.isEmpty() ? null : cats.get(0).categoryId();
            fixes = similarity.findSimilarResolved(title, desc, catId, -1L, 3).stream().map(InsightController::similar).toList();
        }
        return new Analysis(cats, pa, sla, similar, fixes);
    }

    private static SimilarIncident similar(SimilarityService.Match m) {
        Incident i = m.incident();
        return new SimilarIncident(i.getId(), i.getNumber(), i.getTitle(), i.getStatus(), i.getService().getName(), m.score(),
                i.getResolution(), i.getAssignee() == null ? null : i.getAssignee().getFullName(), i.getCreatedAt());
    }

    // ------------------------------------------------ dashboards

    @GetMapping("/reports/dashboard")
    public ReportService.Dashboard dashboard(@RequestParam(defaultValue = "14") int days, Actor actor) {
        if (actor.is(Role.EMPLOYEE)) throw new UnauthorizedOperationException("Dashboards are available to IT staff only");
        return reports.dashboard(Math.min(Math.max(days, 1), 90));
    }

    @GetMapping("/reports/me")
    public ReportService.MyStats myStats(Actor actor) {
        if (!actor.is(Role.TECHNICIAN)) throw new UnauthorizedOperationException("Only technicians have a personal workload");
        return reports.myStats(actor);
    }

    // ------------------------------------------------ notifications

    public record NotificationView(Long id, EventType type, String title, String message, Long incidentId, boolean read, Instant createdAt) {}

    @GetMapping("/notifications")
    public Map<String, Object> notifications(Actor actor) {
        List<NotificationView> items = notifications.inbox(actor.id()).stream().map(n -> new NotificationView(n.getId(), n.getType(),
                n.getTitle(), n.getMessage(), n.getIncidentId(), n.isRead(), n.getCreatedAt())).toList();
        return Map.of("items", items, "unread", notifications.unread(actor.id()));
    }

    @PostMapping("/notifications/{id}/read")
    public void markRead(@PathVariable Long id, Actor actor) { notifications.markRead(id, actor.id()); }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead(Actor actor) { return Map.of("updated", notifications.markAllRead(actor.id())); }

    // ------------------------------------------------ real-time stream

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(Actor actor) { return hub.subscribe(actor.id(), actor.role()); }
}
