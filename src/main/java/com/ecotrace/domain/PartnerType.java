package com.ecotrace.domain;

public enum PartnerType {
    COLLECTION_FACILITY("Collection facility"), RECYCLER("Recycling partner");

    private final String label;

    PartnerType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
