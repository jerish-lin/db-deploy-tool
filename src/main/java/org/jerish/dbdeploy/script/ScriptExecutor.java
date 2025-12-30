package org.jerish.dbdeploy.script;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.database.TransactionManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptExecutor {

    private final DatabaseConnectionManager connectionManager;

    public ScriptExecutionResult executeScript(String scriptPath, String scriptId) {
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setScriptId(scriptId);
        result.setScriptPath(scriptPath);
        result.setStartTime(LocalDateTime.now());

        try {
            String scriptContent = readScriptContent(scriptPath);
            String checksum = calculateChecksum(scriptContent);
            result.setScriptChecksum(checksum);

            try (Connection connection = connectionManager.getConnection()) {
                TransactionManager transactionManager = new TransactionManager(connection);

                transactionManager.executeInTransaction(() -> {
                    try (Statement statement = connection.createStatement()) {
                        String[] sqlStatements = splitStatements(scriptContent);

                        for (String sql : sqlStatements) {
                            if (!sql.trim().isEmpty()) {
                                log.debug("Executing SQL: {}", sql.trim());
                                statement.execute(sql);
                            }
                        }

                        result.setSuccess(true);
                        result.setEndTime(LocalDateTime.now());
                        result.setDuration(System.currentTimeMillis() - startTime);

                        log.info("Script {} executed successfully", scriptId);
                    }
                });
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Script {} execution failed", scriptId, e);
        }

        return result;
    }

//    public void executeScript(String scriptPath) throws Exception {
//        String scriptId = Paths.get(scriptPath).getFileName().toString();
//        ScriptExecutionResult result = executeScript(scriptPath, scriptId);
//
//        if (!result.isSuccess()) {
//            throw new RuntimeException("Script execution failed: " + result.getErrorMessage());
//        }
//    }

    public void executeScriptContent(String scriptContent) throws Exception {
        try (Connection connection = connectionManager.getConnection()) {
            TransactionManager transactionManager = new TransactionManager(connection);

            transactionManager.executeInTransaction(() -> {
                try (Statement statement = connection.createStatement()) {
                    String[] sqlStatements = splitStatements(scriptContent);

                    for (String sql : sqlStatements) {
                        if (!sql.trim().isEmpty()) {
                            log.debug("Executing SQL: {}", sql.trim());
                            statement.execute(sql);
                        }
                    }

                    log.info("Script content executed successfully");
                }
            });
        }
    }

    public String readScriptContent(String scriptPath) throws IOException {
        Path path = Paths.get(scriptPath);
        if (!Files.exists(path)) {
            throw new IOException("Script file not found: " + scriptPath);
        }

        return Files.readString(path);
    }

    public String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String[] splitStatements(String scriptContent) {
        return scriptContent.split(";");
    }

    public VerificationResult executeVerification(String verificationScriptPath) {
        long startTime = System.currentTimeMillis();
        VerificationResult result = new VerificationResult();
        result.setVerificationScriptPath(verificationScriptPath);
        result.setStartTime(LocalDateTime.now());

        try {
            String verificationContent = readScriptContent(verificationScriptPath);
            return executeVerificationContent(verificationContent);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Verification script {} execution failed", verificationScriptPath, e);
            return result;
        }
    }

    public VerificationResult executeVerificationContent(String verificationContent) {
        long startTime = System.currentTimeMillis();
        VerificationResult result = new VerificationResult();
        result.setStartTime(LocalDateTime.now());

        try {
            try (Connection connection = connectionManager.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    String[] sqlStatements = splitStatements(verificationContent);
                    StringBuilder output = new StringBuilder();

                    for (String sql : sqlStatements) {
                        if (!sql.trim().isEmpty()) {
                            log.debug("Executing verification SQL: {}", sql.trim());

                            try (var resultSet = statement.executeQuery(sql.trim())) {
                                while (resultSet.next()) {
                                    if (output.length() > 0) {
                                        output.append("\n");
                                    }
                                    output.append(resultSet.getString(1));
                                }
                            }
                        }
                    }

                    result.setSuccess(true);
                    result.setOutput(output.toString());
                    result.setEndTime(LocalDateTime.now());
                    result.setDuration(System.currentTimeMillis() - startTime);

                    log.info("Verification executed successfully");
                }
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Verification execution failed", e);
        }

        return result;
    }

    public void executeScriptWithVerification(String scriptPath, String scriptId) throws Exception {
        // Execute the main script
        ScriptExecutionResult result = executeScript(scriptPath, scriptId);

        if (!result.isSuccess()) {
            throw new RuntimeException("Script execution failed: " + result.getErrorMessage());
        }

        // Execute verification if it exists
        String verificationPath = scriptPath.replace(".apply.sql", ".apply.verify.sql");
        try {
            if (Files.exists(Paths.get(verificationPath))) {
                VerificationResult verificationResult = executeVerification(verificationPath);

                // Log verification results
                log.info("\n=== VERIFICATION RESULTS ===");
                log.info("Script: {}", scriptId);
                log.info("Verification Status: {}", verificationResult.isSuccess() ? "SUCCESS" : "FAILED");
                log.info("Duration: {}ms", verificationResult.getDuration());
                log.info("Output:");
                log.info("{}", verificationResult.getOutput());

                if (!verificationResult.isSuccess()) {
                    log.error("Error: {}", verificationResult.getErrorMessage());
                }
                log.info("=== END VERIFICATION ===\n");

                // If verification fails, throw exception
                if (!verificationResult.isSuccess()) {
                    throw new RuntimeException("Verification failed for script: " + scriptId);
                }
            } else {
                log.info("No verification script found for: {}", scriptId);
            }
        } catch (Exception e) {
            log.warn("Could not check for verification script: {}", e.getMessage());
        }
    }

    /**
     * Execute script and verification in the same transaction.
     * If verification fails, the entire transaction is rolled back.
     */
    public ScriptExecutionResult executeScriptWithVerificationInTransaction(String scriptPath, String scriptId) {
        long startTime = System.currentTimeMillis();
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setScriptId(scriptId);
        result.setScriptPath(scriptPath);
        result.setStartTime(LocalDateTime.now());

        try {
            String scriptContent = readScriptContent(scriptPath);
            String checksum = calculateChecksum(scriptContent);
            result.setScriptChecksum(checksum);

            // Check if verification script exists
            final String verificationPath = scriptPath.replace(".apply.sql", ".apply.verify.sql");
            final String verificationContent;
            final boolean hasVerification = Files.exists(Paths.get(verificationPath));
            if (hasVerification) {
                verificationContent = readScriptContent(verificationPath);
            } else {
                verificationContent = null;
            }

            try (Connection connection = connectionManager.getConnection()) {
                TransactionManager transactionManager = new TransactionManager(connection);

                transactionManager.executeInTransaction(() -> {
                    try (Statement statement = connection.createStatement()) {
                        // Execute the main script
                        String[] sqlStatements = splitStatements(scriptContent);

                        for (String sql : sqlStatements) {
                            if (!sql.trim().isEmpty()) {
                                log.debug("Executing SQL: {}", sql.trim());
                                statement.execute(sql);
                            }
                        }

                        log.info("Script {} executed successfully", scriptId);

                        // Execute verification if it exists
                        if (hasVerification && verificationContent != null) {
                            log.info("Running verification for script: {}", scriptId);
                            String[] verificationStatements = splitStatements(verificationContent);

                            for (String sql : verificationStatements) {
                                if (!sql.trim().isEmpty()) {
                                    log.debug("Executing verification SQL: {}", sql.trim());
                                    try (ResultSet resultSet = statement.executeQuery(sql.trim())) {
                                        // Consume results to ensure execution
                                        while (resultSet.next()) {
                                            // Just consume the results - verification queries typically return status info
                                        }
                                    }
                                }
                            }
                            log.info("Verification completed successfully for script: {}", scriptId);
                        }

                        result.setSuccess(true);
                        result.setEndTime(LocalDateTime.now());
                        result.setDuration(System.currentTimeMillis() - startTime);
                    }
                });
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Script execution or verification failed: " + e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            log.error("Script {} execution or verification failed", scriptId, e);
        }

        return result;
    }

    @Data
    public static class VerificationResult {
        private String verificationScriptPath;
        private boolean success;
        private String output;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
    }

    @Data
    public static class ScriptExecutionResult {
        private String scriptId;
        private String scriptPath;
        private String scriptChecksum;
        private boolean success;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
    }
}