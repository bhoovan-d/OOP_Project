package edu.oop.itsm.repository;

import edu.oop.itsm.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
