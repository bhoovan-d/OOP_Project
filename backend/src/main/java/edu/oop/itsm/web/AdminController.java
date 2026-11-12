package edu.oop.itsm.web;

import edu.oop.itsm.domain.EventType;
import edu.oop.itsm.domain.Role;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.notification.NotificationService;
import edu.oop.itsm.service.AdminService;
import edu.oop.itsm.web.dto.AdminDtos.*;
import edu.oop.itsm.web.dto.IncidentDtos.AuditView;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Everything under /api/admin requires ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;
    private final NotificationService notifications;

    public AdminController(AdminService admin, NotificationService notifications) {
        this.admin = admin;
        this.notifications = notifications;
    }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam(required = false) Role role) { return admin.users(role); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUser req, Actor actor) { return admin.createUser(req, actor); }

    @PatchMapping("/users/{id}")
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUser req, Actor actor) { return admin.updateUser(id, req, actor); }

    @PostMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPassword req, Actor actor) { admin.resetPassword(id, req.password(), actor); }

    @GetMapping("/services")
    public List<ServiceView> services() { return admin.services(); }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceView createService(@Valid @RequestBody SaveService req, Actor actor) { return admin.saveService(null, req, actor); }

    @PutMapping("/services/{id}")
    public ServiceView updateService(@PathVariable Long id, @Valid @RequestBody SaveService req, Actor actor) { return admin.saveService(id, req, actor); }

    @GetMapping("/sla-policies")
    public List<SlaView> slaPolicies() { return admin.slaPolicies(); }

    @PostMapping("/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SlaView createSla(@Valid @RequestBody SaveSla req, Actor actor) { return admin.saveSla(null, req, actor); }

    @PutMapping("/sla-policies/{id}")
    public SlaView updateSla(@PathVariable Long id, @Valid @RequestBody SaveSla req, Actor actor) { return admin.saveSla(id, req, actor); }

    @GetMapping("/categories")
    public List<CategoryView> categories() { return admin.categories(); }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody SaveCategory req, Actor actor) { return admin.saveCategory(null, req, actor); }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody SaveCategory req, Actor actor) { return admin.saveCategory(id, req, actor); }

    @GetMapping("/settings")
    public SettingsView settings() { return admin.settings(); }

    @PutMapping("/settings")
    public SettingsView updateSettings(@Valid @RequestBody UpdateSettings req, Actor actor) { return admin.updateSettings(req, actor); }

    @GetMapping("/audit")
    public Page<AuditView> audit(@RequestParam(required = false) String incident,
                                 @RequestParam(required = false) EventType action,
                                 @RequestParam(required = false) String actor,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return admin.audit(incident, action, actor, from, to,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by("timestamp").descending().and(Sort.by("id").descending())));
    }

    @GetMapping("/outbox")
    public Page<OutboxView> outbox(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return notifications.outbox(PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200))).map(OutboxView::of);
    }
}
