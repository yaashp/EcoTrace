package com.ecotrace.domain;

import java.util.List;

/** Fixed option lists shared by validation and the booking form. */
public final class Catalog {
    public static final List<String> CATEGORIES = List.of(
            "Mobile phone", "Laptop", "Desktop or monitor", "Printer", "Battery",
            "Cables", "Accessories", "Other electronic device");

    public static final List<String> CONDITIONS = List.of("Working", "Repairable", "Non-functional");

    public static final List<String> SLOTS = List.of("09:00-12:00", "12:00-15:00", "15:00-18:00");

    private Catalog() {
    }
}
