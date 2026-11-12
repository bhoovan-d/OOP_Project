package edu.oop.itsm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Runtime-configurable key/value setting (active assignment strategy, thresholds, ...). */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 60)
    private String key;

    @Column(name = "setting_value", length = 255)
    private String value;

    protected Setting() {}

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
