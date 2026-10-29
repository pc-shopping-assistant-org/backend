package com.ecm.identity.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeTest {

    @Test
    void trimNamesRemovesTheSpacesAroundBothNames() {
        Employee employee = Employee.builder().firstName("  An ").lastName(" Nguyen  ").build();

        employee.trimNames();

        assertEquals("An", employee.getFirstName());
        assertEquals("Nguyen", employee.getLastName());
    }
}
