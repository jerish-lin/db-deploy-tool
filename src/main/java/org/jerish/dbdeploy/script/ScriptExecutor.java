package org.jerish.dbdeploy.script;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic script executor that can execute SQL content on any JdbcTemplate.
 */
@Slf4j
@Service
public class ScriptExecutor {

    /**
     * Execute SQL content on the given JdbcTemplate.
     * Uses execute() for DDL/DML statements.
     *
     * @param jdbcTemplate The JdbcTemplate to execute the SQL on
     * @param sqlContent The SQL content to execute
     * @return ScriptExecutionResult containing execution details
     */
    @Transactional
    public ScriptExecutionResult execute(JdbcTemplate jdbcTemplate, String sqlContent) {
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setStartTime(LocalDateTime.now());

        try {
            String[] sqlStatements = splitStatements(sqlContent);

            for (String sql : sqlStatements) {
                if (!sql.trim().isEmpty()) {
                    String trimmedSql = sql.trim();
                    log.debug("Executing SQL: {}", trimmedSql);
                    jdbcTemplate.execute(trimmedSql);
                }
            }

            result.setSuccess(true);
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.info("Script executed successfully");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Script execution failed", e);
        }

        return result;
    }

    /**
     * Execute verification SQL and capture the output.
     * Uses query() to capture results from SELECT statements.
     * If output contains "FAILED" or "FAIL", marks verification as failed.
     *
     * @param jdbcTemplate The JdbcTemplate to execute the SQL on
     * @param sqlContent The SQL content to execute
     * @return ScriptExecutionResult containing execution details and captured output
     */
    @Transactional
    public ScriptExecutionResult executeVerificationSql(JdbcTemplate jdbcTemplate, String sqlContent) {
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setStartTime(LocalDateTime.now());

        try {
            String[] sqlStatements = splitStatements(sqlContent);
            List<String> outputs = new ArrayList<>();

            for (String sql : sqlStatements) {
                if (!sql.trim().isEmpty()) {
                    String trimmedSql = sql.trim();
                    log.debug("Executing verification SQL: {}", trimmedSql);

                    jdbcTemplate.query(trimmedSql, (ResultSet rs) -> {
                        StringBuilder output = new StringBuilder();
                        while (rs.next()) {
                            if (output.length() > 0) {
                                output.append("\n");
                            }
                            output.append(rs.getString(1));
                        }
                        if (output.length() > 0) {
                            outputs.add(output.toString());
                        }
                    });
                }
            }

            String output = String.join("\n", outputs);
            result.setOutput(output);

            // Check if output contains FAILED or FAIL
            if (output != null && (output.toUpperCase().contains("FAILED") || output.toUpperCase().contains("FAIL"))) {
                result.setSuccess(false);
                result.setErrorMessage("Verification failed: " + output);
                log.error("Verification SQL execution failed with output: {}", output);
            } else {
                result.setSuccess(true);
                log.info("Verification SQL executed successfully");
            }

            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Verification SQL execution failed", e);
        }

        return result;
    }

    /**
     * Split SQL content into individual statements
     *
     * @param scriptContent The SQL content to split
     * @return Array of SQL statements
     */
    private String[] splitStatements(String scriptContent) {
        return scriptContent.split(";");
    }

    /**
     * Calculate SHA-256 checksum of the content
     *
     * @param content The content to calculate checksum for
     * @return Hex string representing the SHA-256 hash
     */
    public String calculateChecksum(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result of script execution
     */
    @Data
    public static class ScriptExecutionResult {
        private boolean success;
        private String errorMessage;
        private String output;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
    }
}