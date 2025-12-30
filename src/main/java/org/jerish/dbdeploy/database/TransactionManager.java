package org.jerish.dbdeploy.database;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class TransactionManager {
    private final Connection connection;

    public TransactionManager(Connection connection) {
        this.connection = connection;
    }

    public void begin() throws SQLException {
        if (connection.getAutoCommit()) {
            connection.setAutoCommit(false);
            log.debug("Transaction started");
        }
    }

    public void commit() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
            connection.setAutoCommit(true);
            log.debug("Transaction committed");
        }
    }

    public void rollback() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
            log.debug("Transaction rolled back");
        }
    }

    @FunctionalInterface
    public interface TransactionOperation {
        void execute() throws SQLException;
    }

    public void executeInTransaction(TransactionOperation operation) throws SQLException {
        boolean wasAutoCommit = connection.getAutoCommit();
        try {
            if (wasAutoCommit) {
                connection.setAutoCommit(false);
            }

            operation.execute();

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            if (wasAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }
}