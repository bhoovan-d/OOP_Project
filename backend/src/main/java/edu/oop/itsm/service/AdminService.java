package edu.oop.itsm.service;

import edu.oop.itsm.assignment.AssignmentService;
import edu.oop.itsm.assignment.AssignmentStrategy;
import edu.oop.itsm.domain.*;
import edu.oop.itsm.event.Actor;
import edu.oop.itsm.exception.DuplicateResourceException;
import edu.oop.itsm.exception.NotFoundException;
import edu.oop.itsm.exception.ValidationException;
import edu.oop.itsm.repository.*;
import edu.oop.itsm.web.dto.AdminDtos.*;
import edu.oop.itsm.web.dto.IncidentDtos;
import edu.oop.itsm.web.dto.IncidentDtos.AuditView;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Configuration and master-data management for administrators. */
@Service
@Transactional
public class AdminService {

    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final CategoryRepository categories;
    private final ItServiceRepository services;
    private final SlaPolicyRepository slaPolicies;
    private final AuditRepository audit;
    private final UserFactory userFactory;
    private final PasswordEncoder encoder;
    private final AssignmentService assignment;
    private final SettingsService settings;
    private final Clock clock;

    public AdminService(UserRepository users, TechnicianRepository technicians, CategoryRepository categories,
                        ItServiceRepository services, SlaPolicyRepository slaPolicies, AuditRepository audit,
                        UserFactory userFactory, PasswordEncoder encoder, AssignmentService assignment,
                        SettingsService settings, Clock clock) {
        this.users = users;
        this.technicians = technicians;
        this.categories = categories;
        this.services = services;
        this.slaPolicies = slaPolicies;
        this.audit = audit;
        this.userFactory = userFactory;
        this.encoder = encoder;
        this.assignment = assignment;
        this.settings = settings;
        this.clock = clock;
    }

    // =========================== users ===========================

    @Transactional(readOnly = true)
    public List<UserView> users(Role role) {
        Map<Long, Long> loads = assignment.currentLoads();
        return users.findAll().stream()
                .filter(u -> role == null || u.getRole() == role)
                .sorted(Comparator.comparing(User::getRole).thenComparing(User::getFullName))
                .map(u -> view(u, loads))
                .toList();
    }

    public UserView createUser(CreateUser req, Actor actor) {
        if (users.existsByEmailIgnoreCase(req.email())) throw new DuplicateResourceException("Email already in use: " + req.email());
        User u = userFactory.create(req.role(), req.fullName().trim(), req.email(), req.password());
        u.setDepartment(req.department());
        u.setPhone(blankToNull(req.phone()));
        applyRoleFields(u, req.skillIds(), req.maxWorkload(), req.experienceYears(), null, req.teamLead(), req.itManager());
        users.save(u);
        record(actor, "Created %s account %s (%s)".formatted(u.getRole(), u.getCode(), u.getEmail()));
        return view(u, assignment.currentLoads());
    }

    public UserView updateUser(Long id, UpdateUser req, Actor actor) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        if (req.fullName() != null) u.setFullName(req.fullName().trim());
        if (req.department() != null) u.setDepartment(req.department());
        if (req.phone() != null) u.setPhone(blankToNull(req.phone()));
        if (req.active() != null) {
            if (!req.active() && u.getId().equals(actor.id())) throw new ValidationException("You cannot deactivate your own account");
            u.setActive(req.active());
        }
        applyRoleFields(u, req.skillIds(), req.maxWorkload(), req.experienceYears(), req.available(), req.teamLead(), req.itManager());
        record(actor, "Updated user %s".formatted(u.getCode()));
        return view(u, assignment.currentLoads());
    }

    public void resetPassword(Long id, String password, Actor actor) {
        User u = users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        u.changePasswordHash(encoder.encode(password));
        record(actor, "Reset password for " + u.getCode());
    }

    private void applyRoleFields(User u, Set<Long> skillIds, Integer max, Integer exp, Boolean available, Boolean lead, Boolean manager) {
        if (u instanceof Technician t) {
            if (skillIds != null) {
                List<Category> found = categories.findAllById(skillIds);
                if (found.size() != skillIds.size()) throw new NotFoundException("One or more skill categories do not exist");
                t.setSkills(new HashSet<>(found));
            }
            if (max != null) t.setMaxWorkload(max);
            if (exp != null) t.setExperienceYears(exp);
            if (available != null) t.setAvailable(available);
            if (lead != null) t.setTeamLead(lead);
        } else if (u instanceof Administrator a && manager != null) {
            a.setItManager(manager);
        }
    }

    private UserView view(User u, Map<Long, Long> loads) {
        if (u instanceof Technician t) {
            return new UserView(t.getId(), t.getCode(), t.getFullName(), t.getEmail(), t.getRole(), t.getDepartment(), t.getPhone(),
                    t.isActive(), t.getCreatedAt(),
                    t.getSkills().stream().sorted(Comparator.comparing(Category::getName)).map(c -> new IncidentDtos.Ref(c.getId(), c.getName())).toList(),
                    t.getMaxWorkload(), t.getExperienceYears(), t.isAvailable(), t.isTeamLead(), loads.getOrDefault(t.getId(), 0L), null);
        }
        Boolean manager = u instanceof Administrator a ? a.isItManager() : null;
        return new UserView(u.getId(), u.getCode(), u.getFullName(), u.getEmail(), u.getRole(), u.getDepartment(), u.getPhone(),
                u.isActive(), u.getCreatedAt(), null, null, null, null, null, null, manager);
    }

    // =========================== services ===========================

    @Transactional(readOnly = true)
    public List<ServiceView> services() {
        return services.findAllByOrderByName().stream().map(ServiceView::of).toList();
    }

    public ServiceView saveService(Long id, SaveService req, Actor actor) {
        ItService s;
        if (id == null) {
            if (services.existsByNameIgnoreCase(req.name())) throw new DuplicateResourceException("Service already exists: " + req.name());
            s = new ItService(req.name().trim(), req.description(), req.criticality());
        } else {
            s = services.findById(id).orElseThrow(() -> new NotFoundException("Service " + id + " not found"));
            services.findByNameIgnoreCase(req.name()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new DuplicateResourceException("Service already exists: " + req.name()); });
            s.setName(req.name().trim());
            s.setDescription(req.description());
            s.setCriticality(req.criticality());
        }
        s.setSlaPolicy(req.slaPolicyId() == null ? null : slaPolicies.findById(req.slaPolicyId())
                .orElseThrow(() -> new NotFoundException("SLA policy " + req.slaPolicyId() + " not found")));
        s.setDefaultCategory(req.defaultCategoryId() == null ? null : categories.findById(req.defaultCategoryId())
                .orElseThrow(() -> new NotFoundException("Category " + req.defaultCategoryId() + " not found")));
        if (req.userBase() != null) s.setUserBase(req.userBase());
        if (req.active() != null) s.setActive(req.active());
        services.save(s);
        record(actor, (id == null ? "Created" : "Updated") + " IT service " + s.getName());
        return ServiceView.of(s);
    }

    // =========================== SLA policies ===========================

    @Transactional(readOnly = true)
    public List<SlaView> slaPolicies() {
        return slaPolicies.findAllByOrderByName().stream().map(SlaView::of).toList();
    }

    public SlaView saveSla(Long id, SaveSla req, Actor actor) {
        Map<Priority, SlaTarget> targets = new EnumMap<>(Priority.class);
        req.targets().forEach((p, t) -> targets.put(p, new SlaTarget(t.responseMinutes(), t.resolutionMinutes())));
        SlaPolicy p;
        if (id == null) {
            if (slaPolicies.existsByNameIgnoreCase(req.name())) throw new DuplicateResourceException("SLA policy already exists: " + req.name());
            p = new SlaPolicy(req.name().trim(), req.description(), targets);
        } else {
            p = slaPolicies.findById(id).orElseThrow(() -> new NotFoundException("SLA policy " + id + " not found"));
            p.setName(req.name().trim());
            p.setDescription(req.description());
            p.setTargets(targets);
        }
        if (req.defaultPolicy()) {
            slaPolicies.findAll().forEach(o -> o.setDefaultPolicy(false));
            p.setDefaultPolicy(true);
        } else if (p.isDefaultPolicy()) {
            throw new ValidationException("Mark another policy as default before un-defaulting this one");
        }
        slaPolicies.save(p);
        record(actor, (id == null ? "Created" : "Updated") + " SLA policy " + p.getName());
        return SlaView.of(p);
    }

    // =========================== categories ===========================

    @Transactional(readOnly = true)
    public List<CategoryView> categories() {
        return categories.findAllByOrderByName().stream().map(CategoryView::of).toList();
    }

    public CategoryView saveCategory(Long id, SaveCategory req, Actor actor) {
        Category c;
        if (id == null) {
            if (categories.existsByNameIgnoreCase(req.name())) throw new DuplicateResourceException("Category already exists: " + req.name());
            c = new Category(req.name().trim(), req.description(), req.keywords());
        } else {
            c = categories.findById(id).orElseThrow(() -> new NotFoundException("Category " + id + " not found"));
            categories.findByNameIgnoreCase(req.name()).filter(o -> !o.getId().equals(id))
                    .ifPresent(o -> { throw new DuplicateResourceException("Category already exists: " + req.name()); });
            c.setName(req.name().trim());
            c.setDescription(req.description());
            c.setKeywords(req.keywords());
        }
        if (req.active() != null) c.setActive(req.active());
        categories.save(c);
        record(actor, (id == null ? "Created" : "Updated") + " category " + c.getName());
        return CategoryView.of(c);
    }

    // =========================== settings ===========================

    @Transactional(readOnly = true)
    public SettingsView settings() {
        String active = assignment.active().key();
        List<StrategyView> list = new ArrayList<>();
        for (AssignmentStrategy s : assignment.strategies())
            list.add(new StrategyView(s.key(), s.displayName(), s.description(), s.key().equals(active)));
        return new SettingsView(active, settings.getDouble(SettingsService.SLA_WARNING_THRESHOLD),
                settings.getBool(SettingsService.ESCALATION_ENABLED), settings.getBool(SettingsService.AUTO_ASSIGN), list);
    }

    public SettingsView updateSettings(UpdateSettings req, Actor actor) {
        if (req.assignmentStrategy() != null) assignment.setActive(req.assignmentStrategy());
        if (req.slaWarningThreshold() != null) settings.set(SettingsService.SLA_WARNING_THRESHOLD, req.slaWarningThreshold().toString());
        if (req.escalationEnabled() != null) settings.set(SettingsService.ESCALATION_ENABLED, req.escalationEnabled().toString());
        if (req.autoAssign() != null) settings.set(SettingsService.AUTO_ASSIGN, req.autoAssign().toString());
        record(actor, "Settings changed: " + req);
        return settings();
    }

    // =========================== audit ===========================

    @Transactional(readOnly = true)
    public Page<AuditView> audit(String incident, EventType action, String actorName, Instant from, Instant to, Pageable pageable) {
        Specification<AuditEntry> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (incident != null && !incident.isBlank()) p.add(cb.like(cb.lower(root.get("incidentNumber")), "%" + incident.trim().toLowerCase() + "%"));
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (actorName != null && !actorName.isBlank()) p.add(cb.like(cb.lower(root.get("actorName")), "%" + actorName.trim().toLowerCase() + "%"));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to != null) p.add(cb.lessThan(root.get("timestamp"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return audit.findAll(spec, pageable).map(AuditView::of);
    }

    /** Administrative actions are audited too (incidentId = null). */
    private void record(Actor actor, String description) {
        audit.save(new AuditEntry(clock.instant(), actor.id(), actor.name(), EventType.UPDATED, null, null, null, null, description));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
