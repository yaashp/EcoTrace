package com.ecotrace.domain;

public enum Role {
    CUSTOMER("Customer"), COLLECTOR("Collector"), RECYCLER("Recycler"), ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Label shown on public tracking pages, where individual names are never shown. */
    public static String publicLabel(String roleName) {
        return switch (roleName) {
            case "CUSTOMER" -> "Customer";
            case "COLLECTOR" -> "Collector";
            case "RECYCLER" -> "Recycling partner";
            case "ADMIN" -> "EcoTrace administrator";
            default -> "System";
        };
    }
}
