package edu.oop.itsm.service;

import edu.oop.itsm.domain.Setting;
import edu.oop.itsm.repository.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime settings with a write-through in-memory cache (read on every SLA tick / assignment). */
@Service
public class SettingsService {

    public static final String ASSIGNMENT_STRATEGY = "assignment.strategy";
    public static final String SLA_WARNING_THRESHOLD = "sla.warningThreshold";
    public static final String ESCALATION_ENABLED = "escalation.enabled";
    public static final String AUTO_ASSIGN = "assignment.auto";

    private static final Map<String, String> DEFAULTS = Map.of(
            ASSIGNMENT_STRATEGY, "smart",
            SLA_WARNING_THRESHOLD, "0.75",
            ESCALATION_ENABLED, "true",
            AUTO_ASSIGN, "true");

    private final SettingRepository repository;
    // ponytail: per-node cache; add a short TTL or pub/sub invalidation when running several instances
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SettingRepository repository) {
        this.repository = repository;
    }

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> repository.findById(k).map(Setting::getValue).orElse(DEFAULTS.get(k)));
    }

    public double getDouble(String key) { return Double.parseDouble(get(key)); }

    public boolean getBool(String key) { return Boolean.parseBoolean(get(key)); }

    @Transactional
    public void set(String key, String value) {
        Setting s = repository.findById(key).orElseGet(() -> new Setting(key, value));
        s.setValue(value);
        repository.save(s);
        cache.put(key, value);
    }

    public Map<String, String> all() {
        return Map.of(
                ASSIGNMENT_STRATEGY, get(ASSIGNMENT_STRATEGY),
                SLA_WARNING_THRESHOLD, get(SLA_WARNING_THRESHOLD),
                ESCALATION_ENABLED, get(ESCALATION_ENABLED),
                AUTO_ASSIGN, get(AUTO_ASSIGN));
    }
}
