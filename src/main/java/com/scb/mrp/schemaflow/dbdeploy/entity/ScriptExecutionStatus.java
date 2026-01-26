package com.scb.mrp.schemaflow.dbdeploy.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScriptExecutionStatus {
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    ROLLED_BACK("ROLLED_BACK"),
    ROLLBACK_FAILED("ROLLBACK_FAILED");

    private final String value;

    public static ScriptExecutionStatus fromValue(String value) {
        for (ScriptExecutionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }
}