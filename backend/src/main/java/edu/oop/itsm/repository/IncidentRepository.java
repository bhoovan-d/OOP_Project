package edu.oop.itsm.repository;

import edu.oop.itsm.domain.Incident;
import edu.oop.itsm.domain.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Repository pattern: persistence hidden behind a collection-like interface (Spring Data generates the implementation). */
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    /** Fetch-joins the to-one associations a list row needs, so a page is one SQL query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"reporter", "category", "service", "assignee"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "category", "service", "assignee", "duplicateOf"})
    Optional<Incident> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"service", "category", "assignee", "reporter"})
    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"service", "category", "assignee"})
    List<Incident> findTop300ByStatusInAndCreatedAtAfterOrderByCreatedAtDesc(Collection<IncidentStatus> statuses, Instant since);

    @EntityGraph(attributePaths = {"service", "category", "assignee"})
    List<Incident> findTop500ByResolutionIsNotNullOrderByResolvedAtDesc();

    long countByServiceIdAndStatusInAndCreatedAtAfter(Long serviceId, Collection<IncidentStatus> statuses, Instant since);

    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.status in :states group by i.assignee.id")
    List<Object[]> countLoadByTechnician(@Param("states") Collection<IncidentStatus> states);

    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.resolvedAt is not null and i.service.id = :serviceId group by i.assignee.id")
    List<Object[]> countResolvedByTechnicianForService(@Param("serviceId") Long serviceId);

    @Query("select i.assignee.id, count(i) from Incident i where i.assignee is not null and i.resolvedAt is not null and i.category.id = :categoryId group by i.assignee.id")
    List<Object[]> countResolvedByTechnicianForCategory(@Param("categoryId") Long categoryId);

    // ---------- reporting ----------

    long countByStatusIn(Collection<IncidentStatus> statuses);

    long countByStatusInAndPriority(Collection<IncidentStatus> statuses, edu.oop.itsm.domain.Priority priority);

    @Query("select count(i) from Incident i where i.responseBreached = true or i.resolutionBreached = true")
    long countBreached();

    @Query("select count(i) from Incident i where i.status in :states and (i.slaStatus = edu.oop.itsm.domain.SlaStatus.AT_RISK)")
    long countAtRisk(@Param("states") Collection<IncidentStatus> states);

    @Query("select i.service.name, count(i), sum(case when i.status in :open then 1 else 0 end), " +
           "sum(case when i.responseBreached = true or i.resolutionBreached = true then 1 else 0 end) " +
           "from Incident i group by i.service.name order by count(i) desc")
    List<Object[]> serviceBreakdown(@Param("open") Collection<IncidentStatus> open);

    @Query("select coalesce(i.category.name, 'Unclassified'), count(i) from Incident i left join i.category group by i.category.name order by count(i) desc")
    List<Object[]> categoryBreakdown();

    @Query("select i.priority, count(i) from Incident i where i.priority is not null and i.status in :states group by i.priority")
    List<Object[]> priorityBreakdown(@Param("states") Collection<IncidentStatus> states);

    @Query("select i.status, count(i) from Incident i group by i.status")
    List<Object[]> statusBreakdown();

    @Query("select i.createdAt, i.resolvedAt, i.priority, i.category.id, i.responseBreached, i.resolutionBreached " +
           "from Incident i where i.createdAt >= :since")
    List<Object[]> timelineSince(@Param("since") Instant since);

    @Query("select i.category.id, i.createdAt, i.resolvedAt from Incident i where i.resolvedAt is not null and i.createdAt >= :since")
    List<Object[]> resolutionDurationsSince(@Param("since") Instant since);

    @Query("select i.assignee.id, count(i), avg(i.feedbackRating), " +
           "sum(case when i.resolutionBreached = true or i.responseBreached = true then 1 else 0 end) " +
           "from Incident i where i.assignee is not null and i.resolvedAt is not null group by i.assignee.id")
    List<Object[]> technicianPerformance();

    @Query("select avg(i.feedbackRating), count(i.feedbackRating) from Incident i where i.feedbackRating is not null")
    List<Object[]> feedbackSummary();
}
