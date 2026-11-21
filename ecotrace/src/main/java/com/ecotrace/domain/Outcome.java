package com.ecotrace.domain;

/** What a recycling partner reports happened to the e-waste. Reuse and repair are not forced into "recycled". */
public enum Outcome {
    RECYCLED("Recycled"),
    COMPONENTS_RECOVERED("Components recovered"),
    REPAIRED("Repaired"),
    REUSED("Reused");

    private final String label;

    Outcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
