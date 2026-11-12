package edu.oop.itsm.web;

import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.domain.Priority;
import edu.oop.itsm.domain.SlaStatus;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.service.IncidentService;
import edu.oop.itsm.web.dto.IncidentDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "priority", "status", "resolutionDueAt", "id");

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@Valid @RequestBody CreateIncident req, Actor actor) {
        return service.create(req, actor);
    }

    /** Search with up to 13 filters, paging and sorting. Employees only ever see their own incidents. */
    @GetMapping
    public Page<Summary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Set<IncidentStatus> status,
            @RequestParam(required = false) Set<Priority> priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) Boolean breached,
            @RequestParam(required = false) Boolean open,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            Actor actor) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        Sort s = Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField).and(Sort.by("id").descending());
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), s);
        return service.search(new Query(q, status, priority, categoryId, serviceId, assigneeId, reporterId, slaStatus,
                breached, open, unassigned, from, to), pr, actor);
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Long id, Actor actor) { return service.get(id, actor); }

    @PatchMapping("/{id}")
    public Detail update(@PathVariable Long id, @Valid @RequestBody UpdateIncident req, Actor actor) { return service.update(id, req, actor); }

    @GetMapping("/{id}/timeline")
    public List<AuditView> timeline(@PathVariable Long id, Actor actor) { return service.timeline(id, actor); }

    @GetMapping("/{id}/recommendations")
    public List<Recommendation> recommendations(@PathVariable Long id, Actor actor) { return service.recommendations(id, actor); }

    @GetMapping("/{id}/suggested-resolutions")
    public List<SimilarIncident> suggestedResolutions(@PathVariable Long id, Actor actor) { return service.suggestedResolutions(id, actor); }

    @GetMapping("/{id}/similar")
    public List<SimilarIncident> similar(@PathVariable Long id, Actor actor) { return service.similarOpen(id, actor); }

    @PostMapping("/{id}/assign")
    public Detail assign(@PathVariable Long id, @RequestBody(required = false) AssignRequest req, Actor actor) {
        return service.assign(id, req == null ? null : req.technicianId(), actor);
    }

    @PostMapping("/{id}/take")
    public Detail take(@PathVariable Long id, Actor actor) { return service.take(id, actor); }

    @PostMapping("/{id}/acknowledge")
    public Detail acknowledge(@PathVariable Long id, Actor actor) { return service.acknowledge(id, actor); }

    @PostMapping("/{id}/start")
    public Detail start(@PathVariable Long id, Actor actor) { return service.start(id, actor); }

    @PostMapping("/{id}/request-info")
    public Detail requestInfo(@PathVariable Long id, @Valid @RequestBody TextRequest req, Actor actor) { return service.requestInfo(id, req.text(), actor); }

    @PostMapping("/{id}/provide-info")
    public Detail provideInfo(@PathVariable Long id, @Valid @RequestBody TextRequest req, Actor actor) { return service.provideInfo(id, req.text(), actor); }

    @PostMapping("/{id}/comments")
    public Detail comment(@PathVariable Long id, @Valid @RequestBody CommentRequest req, Actor actor) { return service.comment(id, req.text(), req.internal(), actor); }

    @PostMapping("/{id}/resolve")
    public Detail resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest req, Actor actor) { return service.resolve(id, req.resolution(), req.rootCause(), actor); }

    @PostMapping("/{id}/verify")
    public Detail verify(@PathVariable Long id, Actor actor) { return service.verify(id, actor); }

    @PostMapping("/{id}/reopen")
    public Detail reopen(@PathVariable Long id, @Valid @RequestBody TextRequest req, Actor actor) { return service.reopen(id, req.text(), actor); }

    @PostMapping("/{id}/close")
    public Detail close(@PathVariable Long id, @Valid @RequestBody(required = false) OptionalTextRequest req, Actor actor) {
        return service.close(id, req == null ? null : req.text(), actor);
    }

    @PostMapping("/{id}/cancel")
    public Detail cancel(@PathVariable Long id, @Valid @RequestBody TextRequest req, Actor actor) { return service.cancel(id, req.text(), actor); }

    @PostMapping("/{id}/merge")
    public Detail merge(@PathVariable Long id, @Valid @RequestBody MergeRequest req, Actor actor) { return service.mergeInto(id, req.duplicateOfId(), actor); }

    @PostMapping("/{id}/feedback")
    public Detail feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest req, Actor actor) { return service.feedback(id, req.rating(), req.comment(), actor); }
}
