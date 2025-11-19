package org.example.model;

public enum ScriptExecutionStatus {
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    ROLLED_BACK("ROLLED_BACK");

    private final String value;

    ScriptExecutionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScriptExecutionStatus fromValue(String value) {
        for (ScriptExecutionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}