package com.ecotrace.domain;

/** SAMPLE entries are demonstration data. VERIFIED can only be set by an administrator with a recorded note. */
public enum Verification {
    SAMPLE("Sample entry, not verified"), PENDING("Verification pending"), VERIFIED("Verified by administrator");

    private final String label;

    Verification(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
