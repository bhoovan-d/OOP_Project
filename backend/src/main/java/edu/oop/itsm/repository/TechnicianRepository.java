package edu.oop.itsm.repository;

import edu.oop.itsm.domain.Technician;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    @EntityGraph(attributePaths = "skills")
    List<Technician> findByActiveTrueOrderByFullName();

    @EntityGraph(attributePaths = "skills")
    List<Technician> findAllByOrderByFullName();

    List<Technician> findByTeamLeadTrueAndActiveTrue();
}
