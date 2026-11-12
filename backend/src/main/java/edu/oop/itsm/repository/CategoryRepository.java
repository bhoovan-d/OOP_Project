package edu.oop.itsm.repository;

import edu.oop.itsm.domain.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @EntityGraph(attributePaths = "keywords")
    List<Category> findByActiveTrueOrderByName();

    @EntityGraph(attributePaths = "keywords")
    List<Category> findAllByOrderByName();

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
