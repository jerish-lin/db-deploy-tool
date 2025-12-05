package org.jerish.dbdeploy.script;

import org.jerish.dbdeploy.database.DatabaseConnectionManager;
import org.jerish.dbdeploy.database.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

public class ScriptExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ScriptExecutor.class);

    private final DatabaseConnectionManager connectionManager;

    public ScriptExecutor(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

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
                                logger.debug("Executing SQL: {}", sql.trim());
                                statement.execute(sql);
                            }
                        }

                        result.setSuccess(true);
                        result.setEndTime(LocalDateTime.now());
                        result.setDuration(System.currentTimeMillis() - startTime);

                        logger.info("Script {} executed successfully", scriptId);
                    }
                });
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            result.setDuration(System.currentTimeMillis() - startTime);

            logger.error("Script {} execution failed", scriptId, e);
        }

        return result;
    }

    public void executeScript(String scriptPath) throws Exception {
        String scriptId = Paths.get(scriptPath).getFileName().toString();
        ScriptExecutionResult result = executeScript(scriptPath, scriptId);

        if (!result.isSuccess()) {
            throw new RuntimeException("Script execution failed: " + result.getErrorMessage());
        }
    }

    public void executeScriptContent(String scriptContent) throws Exception {
        try (Connection connection = connectionManager.getConnection()) {
            TransactionManager transactionManager = new TransactionManager(connection);

            transactionManager.executeInTransaction(() -> {
                try (Statement statement = connection.createStatement()) {
                    String[] sqlStatements = splitStatements(scriptContent);

                    for (String sql : sqlStatements) {
                        if (!sql.trim().isEmpty()) {
                            logger.debug("Executing SQL: {}", sql.trim());
                            statement.execute(sql);
                        }
                    }

                    logger.info("Script content executed successfully");
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

    public static class ScriptExecutionResult {
        private String scriptId;
        private String scriptPath;
        private String scriptChecksum;
        private boolean success;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;

        public String getScriptId() {
            return scriptId;
        }

        public void setScriptId(String scriptId) {
            this.scriptId = scriptId;
        }

        public String getScriptPath() {
            return scriptPath;
        }

        public void setScriptPath(String scriptPath) {
            this.scriptPath = scriptPath;
        }

        public String getScriptChecksum() {
            return scriptChecksum;
        }

        public void setScriptChecksum(String scriptChecksum) {
            this.scriptChecksum = scriptChecksum;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public LocalDateTime getEndTime() {
            return endTime;
        }

        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }
    }
}