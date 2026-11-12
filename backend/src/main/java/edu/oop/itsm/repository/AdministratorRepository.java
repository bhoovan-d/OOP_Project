package edu.oop.itsm.repository;

import edu.oop.itsm.domain.Administrator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdministratorRepository extends JpaRepository<Administrator, Long> {
    List<Administrator> findByActiveTrue();
    List<Administrator> findByItManagerTrueAndActiveTrue();
    List<Administrator> findByItManagerFalseAndActiveTrue();
}
