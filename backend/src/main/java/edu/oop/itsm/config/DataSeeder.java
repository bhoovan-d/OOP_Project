package edu.oop.itsm.config;

import edu.oop.itsm.domain.*;
import edu.oop.itsm.intelligence.CategoryClassifier;
import edu.oop.itsm.intelligence.PriorityEngine;
import edu.oop.itsm.repository.*;
import edu.oop.itsm.service.UserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Seeds a realistic organisation on first start: catalogue, SLA policies, staff and ~3 weeks of incident
 * history walked through the real domain lifecycle (with back-dated timestamps and audit trail),
 * so dashboards, MTTR, duplicate detection and suggested resolutions have data from minute one.
 */
@Component
@ConditionalOnProperty(name = "itsm.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final CategoryRepository categories;
    private final SlaPolicyRepository slaPolicies;
    private final ItServiceRepository services;
    private final IncidentRepository incidents;
    private final AuditRepository audit;
    private final UserFactory userFactory;
    private final CategoryClassifier classifier;
    private final PriorityEngine priorityEngine;
    private final Clock clock;
    private final Random rnd = new Random(42);

    public DataSeeder(UserRepository users, CategoryRepository categories, SlaPolicyRepository slaPolicies, ItServiceRepository services,
                      IncidentRepository incidents, AuditRepository audit, UserFactory userFactory, CategoryClassifier classifier,
                      PriorityEngine priorityEngine, Clock clock) {
        this.users = users;
        this.categories = categories;
        this.slaPolicies = slaPolicies;
        this.services = services;
        this.incidents = incidents;
        this.audit = audit;
        this.userFactory = userFactory;
        this.classifier = classifier;
        this.priorityEngine = priorityEngine;
        this.clock = clock;
    }

    private record Template(String service, String title, String description, Impact impact, Urgency urgency, int users,
                            String resolution, String rootCause) {}

    private static final List<Template> TEMPLATES = List.of(
            new Template("Payment Gateway", "Payment application unavailable", "The payment application is unavailable and users are unable to complete transactions.", Impact.EXTENSIVE, Urgency.CRITICAL, 2000, "Restarted payment-api pods and rolled back faulty release 4.2.1", "Bad deployment exhausted the DB connection pool"),
            new Template("Payment Gateway", "Card payments timing out at checkout", "Customers report card payments timing out after 30 seconds at checkout.", Impact.HIGH, Urgency.HIGH, 400, "Increased gateway timeout and optimised the slow fraud-check query", "Fraud-check service latency"),
            new Template("Customer Database", "Customer DB queries extremely slow", "CRM screens take over a minute to load; database queries are slow.", Impact.HIGH, Urgency.HIGH, 300, "Rebuilt fragmented indexes and refreshed table statistics", "Index fragmentation after bulk import"),
            new Template("Customer Database", "Database replication lag on read replica", "Read replica is 20 minutes behind the primary database; reports show stale data.", Impact.MEDIUM, Urgency.MEDIUM, 40, "Resized replica instance and tuned WAL settings", "Replica under-provisioned"),
            new Template("SSO & Identity", "Unable to login via SSO", "Users get an invalid SAML response error when signing in through SSO.", Impact.HIGH, Urgency.CRITICAL, 600, "Renewed the expired IdP signing certificate", "Expired SAML signing certificate"),
            new Template("SSO & Identity", "MFA OTP codes not arriving", "OTP messages for multi-factor login are not delivered to several users.", Impact.MEDIUM, Urgency.HIGH, 60, "Switched OTP SMS provider route", "SMS provider regional outage"),
            new Template("SSO & Identity", "Account locked after password reset", "My account is locked after resetting my password this morning.", Impact.LOW, Urgency.MEDIUM, 1, "Unlocked account and cleared cached credentials on devices", "Old password cached on mobile mail client"),
            new Template("Corporate Email", "Outlook not syncing emails", "Outlook desktop has not synced new emails since the morning.", Impact.LOW, Urgency.MEDIUM, 1, "Rebuilt Outlook profile and OST cache file", "Corrupted OST cache"),
            new Template("Corporate Email", "Email delivery delayed for whole team", "Emails to external domains are delayed by over an hour for the sales team.", Impact.MEDIUM, Urgency.HIGH, 80, "Cleared stuck transport queue and fixed SPF record", "Misconfigured SPF record after DNS change"),
            new Template("VPN Access", "VPN connection drops every few minutes", "Remote VPN keeps disconnecting, cannot work from home.", Impact.MEDIUM, Urgency.HIGH, 50, "Updated VPN client and adjusted keep-alive interval", "Client keep-alive mismatch after gateway upgrade"),
            new Template("VPN Access", "Cannot connect to VPN", "VPN client shows authentication failed error when connecting.", Impact.LOW, Urgency.HIGH, 1, "Re-enrolled the user VPN certificate", "Expired user certificate"),
            new Template("Cloud Storage", "Shared drive running out of space", "Finance shared drive shows quota exceeded; cannot save files.", Impact.MEDIUM, Urgency.MEDIUM, 25, "Increased quota and archived old files", "Quota not raised after team growth"),
            new Template("Cloud Storage", "Files failing to upload to cloud storage", "Uploads to cloud storage fail with error 500 for the whole department.", Impact.MEDIUM, Urgency.HIGH, 120, "Rotated expired storage access keys", "Expired service credentials"),
            new Template("Kubernetes Platform", "Kubernetes pods crash looping in production", "Several pods in the production cluster are in CrashLoopBackOff.", Impact.HIGH, Urgency.CRITICAL, 200, "Raised memory limits and fixed leak in logging sidecar", "Memory leak in logging sidecar"),
            new Template("Kubernetes Platform", "Cloud deployment pipeline failing", "CI/CD deployments to the cloud cluster fail at image pull.", Impact.MEDIUM, Urgency.HIGH, 30, "Refreshed container registry pull secret", "Registry token expired"),
            new Template("Office Network & Wi-Fi", "Wi-Fi very slow on 3rd floor", "Wireless network on the 3rd floor is extremely slow with high latency.", Impact.MEDIUM, Urgency.MEDIUM, 60, "Replaced faulty access point and rebalanced channels", "Faulty wireless access point"),
            new Template("Office Network & Wi-Fi", "Internet outage in branch office", "No internet connectivity at the Pune branch office network.", Impact.HIGH, Urgency.HIGH, 150, "ISP restored the fibre link; configured 4G failover", "ISP fibre cut"),
            new Template("End-user Devices", "Laptop battery not charging", "My laptop battery is not charging even when plugged in.", Impact.LOW, Urgency.LOW, 1, "Replaced charger and updated BIOS", "Faulty charger"),
            new Template("End-user Devices", "Printer not working on 2nd floor", "The 2nd floor printer shows offline and print jobs are stuck.", Impact.LOW, Urgency.MEDIUM, 20, "Cleared print spooler and reassigned static IP", "IP address conflict"),
            new Template("End-user Devices", "External monitor flickering", "External monitor keeps flickering when the laptop is docked.", Impact.LOW, Urgency.LOW, 1, "Replaced docking station cable", "Damaged DisplayPort cable"),
            new Template("End-user Devices", "Excel crashes when opening large files", "Excel software crashes every time I open a large spreadsheet.", Impact.LOW, Urgency.MEDIUM, 1, "Installed Office update and disabled faulty add-in", "Incompatible Excel add-in"),
            new Template("ERP System", "ERP invoice module throwing errors", "Generating invoices in the ERP application throws a server error page.", Impact.MEDIUM, Urgency.HIGH, 35, "Applied vendor hotfix for the invoice module", "Known bug in ERP patch 12.3"),
            new Template("HR Portal", "HR portal leave page not loading", "The leave application page in the HR portal shows a blank page.", Impact.LOW, Urgency.MEDIUM, 15, "Cleared CDN cache and redeployed the portal frontend", "Stale CDN assets"),
            new Template("Security Operations", "Suspicious phishing email received", "Many employees received a phishing email asking for their passwords.", Impact.HIGH, Urgency.HIGH, 300, "Blocked sender domain, purged emails and sent an awareness notice", "Targeted phishing campaign"),
            new Template("Security Operations", "Malware detected on workstation", "Antivirus flagged malware on a finance workstation.", Impact.LOW, Urgency.CRITICAL, 1, "Isolated machine, re-imaged it and reset credentials", "Malicious email attachment"));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        Instant now = clock.instant();
        log.info("Seeding demo organisation...");

        Map<String, Category> cat = new HashMap<>();
        for (Category c : List.of(
                new Category("Hardware", "Physical devices and peripherals", List.of("laptop", "desktop", "monitor", "keyboard", "mouse", "printer", "hardware", "battery", "charger", "docking", "device", "screen")),
                new Category("Software", "Desktop software and licences", List.of("software", "install", "license", "update", "office", "excel", "word", "crash", "upgrade", "add-in", "outlook")),
                new Category("Network", "LAN, Wi-Fi, VPN and internet", List.of("network", "wifi", "internet", "vpn", "latency", "dns", "router", "switch", "connectivity", "firewall", "fibre", "access point")),
                new Category("Database", "Database servers and data", List.of("database", "sql", "query", "queries", "db", "replica", "replication", "index", "deadlock", "table", "crm")),
                new Category("Cloud", "Cloud platforms and containers", List.of("cloud", "aws", "azure", "kubernetes", "pod", "pods", "container", "cluster", "deployment", "pipeline", "registry")),
                new Category("Security", "Threats, malware and access abuse", List.of("security", "virus", "malware", "phishing", "breach", "suspicious", "ransomware", "attack", "antivirus", "vulnerability")),
                new Category("Authentication", "Login, SSO, MFA and accounts", List.of("login", "password", "sso", "mfa", "otp", "account", "locked", "saml", "certificate", "credential", "signin")),
                new Category("Application", "Business applications and portals", List.of("application", "payment", "checkout", "transaction", "erp", "portal", "invoice", "website", "page", "card")),
                new Category("Storage", "File shares, quotas and backups", List.of("storage", "disk", "drive", "space", "quota", "backup", "share", "file", "files", "upload")))) {
            cat.put(c.getName(), categories.save(c));
        }

        SlaPolicy gold = slaPolicies.save(new SlaPolicy("Gold", "Mission-critical services (24x7)", targets(5, 30, 15, 120, 60, 480, 240, 1440)));
        SlaPolicy standard = new SlaPolicy("Standard", "Default business-hours services", targets(15, 60, 30, 240, 120, 960, 480, 2880));
        standard.setDefaultPolicy(true);
        standard = slaPolicies.save(standard);
        SlaPolicy basic = slaPolicies.save(new SlaPolicy("Basic", "Low-impact internal services", targets(30, 240, 60, 480, 240, 1440, 960, 4320)));

        Map<String, ItService> svc = new HashMap<>();
        Object[][] serviceRows = {
                {"Payment Gateway", "Online and in-store payment processing", Criticality.MISSION_CRITICAL, gold, "Application", 5000},
                {"Customer Database", "Primary customer / CRM database cluster", Criticality.MISSION_CRITICAL, gold, "Database", 3000},
                {"SSO & Identity", "Single sign-on, MFA and directory", Criticality.CRITICAL, gold, "Authentication", 1500},
                {"Kubernetes Platform", "Production container platform", Criticality.CRITICAL, gold, "Cloud", 200},
                {"Security Operations", "Threat detection and response", Criticality.CRITICAL, gold, "Security", 1500},
                {"ERP System", "Finance, procurement and invoicing", Criticality.CRITICAL, standard, "Application", 800},
                {"Corporate Email", "Exchange / Outlook mail", Criticality.HIGH, standard, "Software", 1500},
                {"VPN Access", "Remote access gateway", Criticality.HIGH, standard, "Network", 900},
                {"Cloud Storage", "Shared drives and object storage", Criticality.HIGH, standard, "Storage", 1200},
                {"Office Network & Wi-Fi", "Campus LAN, Wi-Fi and internet", Criticality.MODERATE, standard, "Network", 1500},
                {"HR Portal", "Leave, payroll and employee self-service", Criticality.MODERATE, basic, "Application", 1500},
                {"End-user Devices", "Laptops, desktops, printers and peripherals", Criticality.LOW, basic, "Hardware", 1500}};
        for (Object[] r : serviceRows) {
            ItService s = new ItService((String) r[0], (String) r[1], (Criticality) r[2]);
            s.setSlaPolicy((SlaPolicy) r[3]);
            s.setDefaultCategory(cat.get((String) r[4]));
            s.setUserBase((Integer) r[5]);
            svc.put(s.getName(), services.save(s));
        }

        Administrator admin = (Administrator) user(Role.ADMIN, "Ira Kapoor", "admin@itsm.local", "Admin@123", "IT Operations", "+91 98100 00001");
        Administrator manager = (Administrator) user(Role.ADMIN, "Dev Malhotra", "manager@itsm.local", "Admin@123", "IT Management", "+91 98100 00002");
        manager.setItManager(true);

        List<Technician> techs = new ArrayList<>();
        Object[][] techRows = {
                {"Priya Sharma", "priya", 9, true, 6, List.of("Network", "Cloud")},
                {"Arjun Mehta", "arjun", 6, false, 5, List.of("Database", "Cloud", "Storage")},
                {"Neha Iyer", "neha", 5, false, 5, List.of("Application", "Software")},
                {"Rahul Verma", "rahul", 2, false, 6, List.of("Hardware", "Software")},
                {"Kavya Nair", "kavya", 8, true, 5, List.of("Authentication", "Security")},
                {"Vikram Rao", "vikram", 3, false, 5, List.of("Storage", "Database")},
                {"Ananya Das", "ananya", 4, false, 5, List.of("Application", "Authentication", "Cloud")},
                {"Sameer Khan", "sameer", 1, false, 4, List.of("Network", "Hardware", "Security")}};
        int phone = 10;
        for (Object[] r : techRows) {
            Technician t = (Technician) user(Role.TECHNICIAN, (String) r[0], r[1] + "@itsm.local", "Tech@123", "IT Support", "+91 98200 000" + phone++);
            t.setExperienceYears((Integer) r[2]);
            t.setTeamLead((Boolean) r[3]);
            t.setMaxWorkload((Integer) r[4]);
            Set<Category> skills = new HashSet<>();
            for (Object n : (List<?>) r[5]) skills.add(cat.get((String) n));
            t.setSkills(skills);
            techs.add(t);
        }

        List<User> employees = new ArrayList<>();
        Object[][] empRows = {
                {"Rohan Gupta", "rohan", "Finance"}, {"Sneha Kulkarni", "sneha", "Sales"}, {"Aditya Singh", "aditya", "HR"},
                {"Meera Joshi", "meera", "Operations"}, {"Karan Malhotra", "karan", "Engineering"}, {"Pooja Reddy", "pooja", "Marketing"}};
        phone = 30;
        for (Object[] r : empRows)
            employees.add(user(Role.EMPLOYEE, (String) r[0], r[1] + "@itsm.local", "User@123", (String) r[2], "+91 98300 000" + phone++));
        users.flush();

        List<Category> activeCats = categories.findByActiveTrueOrderByName();
        Map<Long, Integer> openLoad = new HashMap<>();

        // ---- ~3 weeks of closed history ----
        for (int n = 0; n < 90; n++) {
            Template t = TEMPLATES.get(rnd.nextInt(TEMPLATES.size()));
            Instant created = now.minus(Duration.ofDays(1 + rnd.nextInt(21))).minus(Duration.ofMinutes(rnd.nextInt(600)));
            Incident i = open(t, svc.get(t.service()), pick(employees), created, activeCats, techs, admin, openLoad, false);
            if (i == null) continue;
            completeLifecycle(i, t, created, rnd.nextInt(10) < 8);
        }

        // ---- currently open work in various states ----
        User rohan = employees.get(0);
        int[][] openPlan = { // template, minutesAgo, targetState(0=assigned,1=ack,2=progress,3=awaiting,4=resolved,5=queued)
                {7, 50, 4}, {17, 95, 4}, {10, 40, 3}, {3, 70, 2}, {12, 160, 2}, {14, 25, 2}, {19, 35, 1}, {21, 18, 1},
                {8, 30, 0}, {15, 22, 0}, {18, 12, 0}, {20, 6, 5}, {5, 45, 2}, {9, 140, 2}};
        for (int k = 0; k < openPlan.length; k++) {
            Template t = TEMPLATES.get(openPlan[k][0]);
            Instant created = now.minus(Duration.ofMinutes(openPlan[k][1]));
            User reporter = k < 4 ? rohan : employees.get(1 + k % (employees.size() - 1));
            Incident i = open(t, svc.get(t.service()), reporter, created, activeCats, techs, admin, openLoad, openPlan[k][2] == 5);
            if (i == null || i.getAssignee() == null) continue;
            Technician tech = i.getAssignee();
            int state = openPlan[k][2];
            Instant at = created.plus(Duration.ofMinutes(Math.max(1, Math.min(openPlan[k][1] / 4, i.getResponseTargetMinutes() / 2))));
            if (state >= 1) step(i, EventType.ACKNOWLEDGED, tech, at, x -> x.acknowledge(at), "Technician acknowledged incident");
            Instant at2 = at.plus(Duration.ofMinutes(2));
            if (state >= 2) step(i, EventType.WORK_STARTED, tech, at2, x -> x.startWork(at2), "Investigation started");
            if (state >= 2) i.addComment(tech, CommentType.INVESTIGATION_NOTE, "Checked logs and recent changes; narrowing down the cause.", at2.plusSeconds(60));
            if (state == 3) {
                Instant at3 = at2.plus(Duration.ofMinutes(5));
                i.addComment(tech, CommentType.INFO_REQUEST, "Could you share a screenshot of the error and the time it last happened?", at3);
                step(i, EventType.INFO_REQUESTED, tech, at3, x -> x.requestInfo(at3), "Information requested from reporter (SLA clock paused)");
            }
            if (state == 4) {
                Instant at4 = at2.plus(Duration.ofMinutes(Math.max(3, openPlan[k][1] / 2)));
                step(i, EventType.RESOLVED, tech, at4, x -> x.resolve(t.resolution(), t.rootCause(), at4), "Resolution provided; incident marked RESOLVED");
                i.addComment(tech, CommentType.RESOLUTION, t.resolution(), at4);
            }
        }

        // ---- the live scenario from the brief: payment app down, 8 minutes ago ----
        Template pay = TEMPLATES.get(0);
        open(pay, svc.get(pay.service()), employees.get(1), now.minus(Duration.ofMinutes(8)), activeCats, techs, admin, openLoad, false);

        log.info("Seeded {} users, {} services, {} incidents", users.count(), services.count(), incidents.count());
    }

    // --------------------------------------------------------------------------------------------

    private Incident open(Template t, ItService service, User reporter, Instant created, List<Category> cats, List<Technician> techs,
                          Administrator admin, Map<Long, Integer> openLoad, boolean leaveQueued) {
        Incident i = incidents.save(new Incident(t.title(), t.description(), reporter, service, t.impact(), t.urgency(),
                Math.max(1, t.users() + rnd.nextInt(Math.max(1, t.users() / 4 + 1)) - t.users() / 8), created));
        audit(i, EventType.CREATED, null, reporter, created, "%s created by %s".formatted(i.getNumber(), reporter.getFullName()));

        var s = classifier.suggest(t.title(), t.description(), service, cats);
        Category c = s.isEmpty() ? service.getDefaultCategory() : cats.stream().filter(x -> x.getId().equals(s.get(0).categoryId())).findFirst().orElseThrow();
        double conf = s.isEmpty() ? 0 : s.get(0).confidence();
        Instant t1 = created.plusSeconds(20);
        step(i, EventType.CLASSIFIED, null, t1, x -> x.classify(c, true, conf, t1), "Incident auto-classified as %s (%.0f%% confidence)".formatted(c.getName().toUpperCase(), conf * 100));

        var a = priorityEngine.assess(i.getImpact(), i.getUrgency(), service.getCriticality(), i.getAffectedUsers());
        i.applyPriority(a.priority(), a.score(), a.explanation());
        i.applySla(service.getSlaPolicy());
        audit(i, EventType.PRIORITIZED, null, null, t1, "Priority calculated as %s (%s), score %.1f".formatted(a.priority(), a.priority().label(), a.score()));
        audit(i, EventType.SLA_APPLIED, null, null, t1, "SLA '%s' applied: respond within %d min, resolve within %d min"
                .formatted(service.getSlaPolicy().getName(), i.getResponseTargetMinutes(), i.getResolutionTargetMinutes()));
        if (leaveQueued) {
            audit(i, EventType.UNASSIGNED_QUEUED, null, null, t1, "No available technician with spare capacity - queued for manual assignment");
            return i;
        }

        List<Technician> skilled = techs.stream().filter(x -> x.hasSkill(c)).toList();
        List<Technician> pool = skilled.isEmpty() ? techs : skilled;
        Technician tech = pool.stream().min(Comparator.comparingInt(x -> openLoad.getOrDefault(x.getId(), 0) * 10 + rnd.nextInt(10))).orElseThrow();
        Instant t2 = created.plusSeconds(60);
        step(i, EventType.ASSIGNED, null, t2, x -> x.assignTo(tech, t2), "Assigned to Technician %s (%s) via Smart (weighted) strategy".formatted(tech.getCode(), tech.getFullName()));
        openLoad.merge(tech.getId(), 1, Integer::sum);
        return i;
    }

    private void completeLifecycle(Incident i, Template t, Instant created, boolean feedback) {
        Technician tech = i.getAssignee();
        Instant ack = created.plusSeconds((long) (i.getResponseTargetMinutes() * 60 * (0.15 + rnd.nextDouble() * 1.2)));
        step(i, EventType.ACKNOWLEDGED, tech, ack, x -> x.acknowledge(ack), "Technician acknowledged incident");
        Instant start = ack.plusSeconds(120);
        step(i, EventType.WORK_STARTED, tech, start, x -> x.startWork(start), "Investigation started");
        i.addComment(tech, CommentType.INVESTIGATION_NOTE, "Root cause identified: " + t.rootCause(), start.plusSeconds(300));
        Instant res0 = created.plusSeconds((long) (i.getResolutionTargetMinutes() * 60 * (0.15 + rnd.nextDouble() * 1.05)));
        Instant resolved = res0.isAfter(start.plusSeconds(600)) ? res0 : start.plusSeconds(600);
        step(i, EventType.RESOLVED, tech, resolved, x -> x.resolve(t.resolution(), t.rootCause(), resolved), "Resolution provided; incident marked RESOLVED");
        i.addComment(tech, CommentType.RESOLUTION, t.resolution(), resolved);
        User reporter = i.getReporter();
        Instant verified = resolved.plus(Duration.ofMinutes(30 + rnd.nextInt(240)));
        if (feedback) {
            int rating = 5 - (rnd.nextInt(10) < 6 ? 0 : 1 + rnd.nextInt(2));
            i.recordFeedback(rating, rating >= 4 ? "Quick and clear fix, thanks!" : "Resolved, but it took a while.");
            audit(i, EventType.FEEDBACK, null, reporter, verified, "Feedback %d/5".formatted(rating));
        }
        step(i, EventType.VERIFIED, reporter, verified, x -> x.verify(verified), "Resolution confirmed by reporter");
        step(i, EventType.CLOSED, reporter, verified.plusSeconds(5), x -> x.close("Closed after verification", verified.plusSeconds(5)), "Incident CLOSED");
    }

    private void step(Incident i, EventType type, User actor, Instant at, java.util.function.Consumer<Incident> action, String detail) {
        IncidentStatus from = i.getStatus();
        action.accept(i);
        audit(i, type, from, actor, at, detail);
    }

    private void audit(Incident i, EventType type, IncidentStatus from, User actor, Instant at, String detail) {
        audit.save(new AuditEntry(at, actor == null ? null : actor.getId(), actor == null ? "System" : actor.getFullName(), type,
                i.getId(), i.getNumber(), from, i.getStatus(), detail));
    }

    private User user(Role role, String name, String email, String password, String dept, String phone) {
        User u = userFactory.create(role, name, email, password);
        u.setDepartment(dept);
        u.setPhone(phone);
        return users.save(u);
    }

    private <T> T pick(List<T> list) { return list.get(rnd.nextInt(list.size())); }

    private static Map<Priority, SlaTarget> targets(int r1, int s1, int r2, int s2, int r3, int s3, int r4, int s4) {
        Map<Priority, SlaTarget> m = new EnumMap<>(Priority.class);
        m.put(Priority.P1, new SlaTarget(r1, s1));
        m.put(Priority.P2, new SlaTarget(r2, s2));
        m.put(Priority.P3, new SlaTarget(r3, s3));
        m.put(Priority.P4, new SlaTarget(r4, s4));
        return m;
    }
}
