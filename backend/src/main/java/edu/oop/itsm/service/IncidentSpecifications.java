package edu.oop.itsm.service;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import edu.oop.itsm.web.dto.IncidentDtos.Query;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translates the search form (13 optional criteria) into one SQL WHERE clause. */
final class IncidentSpecifications {

    private static final Pattern NUMBER = Pattern.compile("^\\s*(?:INC)?-?\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private IncidentSpecifications() {}

    static Specification<Incident> of(Query q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q.q() != null && !q.q().isBlank()) {
                Matcher m = NUMBER.matcher(q.q());
                if (m.matches()) {
                    long n = Long.parseLong(m.group(1));
                    p.add(cb.equal(root.get("id"), n >= 1000 ? n - 1000 : n));
                } else {
                    String like = "%" + q.q().trim().toLowerCase() + "%";
                    p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("description")), like)));
                }
            }
            if (q.status() != null && !q.status().isEmpty()) p.add(root.get("status").in(q.status()));
            if (q.priority() != null && !q.priority().isEmpty()) p.add(root.get("priority").in(q.priority()));
            if (q.categoryId() != null) p.add(cb.equal(root.get("category").get("id"), q.categoryId()));
            if (q.serviceId() != null) p.add(cb.equal(root.get("service").get("id"), q.serviceId()));
            if (q.assigneeId() != null) p.add(cb.equal(root.get("assignee").get("id"), q.assigneeId()));
            if (q.reporterId() != null) p.add(cb.equal(root.get("reporter").get("id"), q.reporterId()));
            if (q.slaStatus() != null) p.add(cb.equal(root.get("slaStatus"), q.slaStatus()));
            if (Boolean.TRUE.equals(q.breached())) p.add(cb.or(cb.isTrue(root.get("responseBreached")), cb.isTrue(root.get("resolutionBreached"))));
            if (Boolean.TRUE.equals(q.open())) p.add(root.get("status").in(IncidentStatus.openStates()));
            if (Boolean.FALSE.equals(q.open())) p.add(cb.not(root.get("status").in(IncidentStatus.openStates())));
            if (Boolean.TRUE.equals(q.unassigned())) p.add(cb.isNull(root.get("assignee")));
            if (q.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.from()));
            if (q.to() != null) p.add(cb.lessThan(root.get("createdAt"), q.to()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
