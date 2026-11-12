package edu.oop.itsm.web.dto;

import edu.oop.itsm.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdminDtos {

    private AdminDtos() {}

    // ---------- users ----------

    public record UserView(Long id, String code, String fullName, String email, Role role, String department, String phone,
                           boolean active, Instant createdAt,
                           // technician-only
                           List<IncidentDtos.Ref> skills, Integer maxWorkload, Integer experienceYears, Boolean available,
                           Boolean teamLead, Long activeLoad,
                           // admin-only
                           Boolean itManager) {}

    public record CreateUser(
            @NotNull Role role,
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password,
            @Size(max = 60) String department,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            Set<Long> skillIds, @Min(1) @Max(50) Integer maxWorkload, @Min(0) @Max(60) Integer experienceYears,
            Boolean teamLead, Boolean itManager) {}

    public record UpdateUser(
            @Size(min = 2, max = 100) String fullName,
            @Size(max = 60) String department,
            @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            Boolean active,
            Set<Long> skillIds, @Min(1) @Max(50) Integer maxWorkload, @Min(0) @Max(60) Integer experienceYears,
            Boolean available, Boolean teamLead, Boolean itManager) {}

    public record ResetPassword(@NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String password) {}

    // ---------- services ----------

    public record ServiceView(Long id, String name, String description, Criticality criticality, IncidentDtos.Ref slaPolicy,
                              IncidentDtos.Ref defaultCategory, int userBase, boolean active) {
        public static ServiceView of(ItService s) {
            return new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getCriticality(),
                    s.getSlaPolicy() == null ? null : new IncidentDtos.Ref(s.getSlaPolicy().getId(), s.getSlaPolicy().getName()),
                    s.getDefaultCategory() == null ? null : new IncidentDtos.Ref(s.getDefaultCategory().getId(), s.getDefaultCategory().getName()),
                    s.getUserBase(), s.isActive());
        }
    }

    public record SaveService(@NotBlank @Size(max = 80) String name, @Size(max = 255) String description,
                              @NotNull Criticality criticality, Long slaPolicyId, Long defaultCategoryId,
                              @Min(0) Integer userBase, Boolean active) {}

    // ---------- SLA ----------

    public record Target(@Min(1) int responseMinutes, @Min(1) int resolutionMinutes) {}

    public record SlaView(Long id, String name, String description, boolean defaultPolicy, Map<Priority, Target> targets) {
        public static SlaView of(SlaPolicy p) {
            Map<Priority, Target> t = new java.util.EnumMap<>(Priority.class);
            p.getTargets().forEach((k, v) -> t.put(k, new Target(v.getResponseMinutes(), v.getResolutionMinutes())));
            return new SlaView(p.getId(), p.getName(), p.getDescription(), p.isDefaultPolicy(), t);
        }
    }

    public record SaveSla(@NotBlank @Size(max = 60) String name, @Size(max = 255) String description, boolean defaultPolicy,
                          @NotNull Map<Priority, @Valid Target> targets) {}

    // ---------- categories ----------

    public record CategoryView(Long id, String name, String description, List<String> keywords, boolean active) {
        public static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getName(), c.getDescription(), List.copyOf(c.getKeywords()), c.isActive());
        }
    }

    public record SaveCategory(@NotBlank @Size(max = 60) String name, @Size(max = 255) String description,
                               List<@Size(max = 40) String> keywords, Boolean active) {}

    // ---------- settings ----------

    public record StrategyView(String key, String name, String description, boolean active) {}

    public record SettingsView(String assignmentStrategy, double slaWarningThreshold, boolean escalationEnabled, boolean autoAssign,
                               List<StrategyView> strategies) {}

    public record UpdateSettings(String assignmentStrategy, @DecimalMin("0.3") @DecimalMax("0.95") Double slaWarningThreshold,
                                 Boolean escalationEnabled, Boolean autoAssign) {}

    // ---------- outbox ----------

    public record OutboxView(Long id, ChannelType channel, String recipient, EventType type, String title, String message,
                             Long incidentId, String externalId, Instant createdAt) {
        public static OutboxView of(Notification n) {
            return new OutboxView(n.getId(), n.getChannel(), n.getRecipientAddress(), n.getType(), n.getTitle(), n.getMessage(),
                    n.getIncidentId(), n.getExternalId(), n.getCreatedAt());
        }
    }
}
