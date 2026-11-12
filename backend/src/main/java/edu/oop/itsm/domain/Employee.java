package edu.oop.itsm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    protected Employee() {}

    public Employee(String fullName, String email, String passwordHash) {
        super(fullName, email, passwordHash);
    }

    @Override public Role getRole() { return Role.EMPLOYEE; }
}
