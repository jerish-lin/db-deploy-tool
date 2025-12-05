package org.jerish.dbdeploy.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionManager {
    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);

    private final Connection connection;

    public TransactionManager(Connection connection) {
        this.connection = connection;
    }

    public void begin() throws SQLException {
        if (connection.getAutoCommit()) {
            connection.setAutoCommit(false);
            logger.debug("Transaction started");
        }
    }

    public void commit() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
            connection.setAutoCommit(true);
            logger.debug("Transaction committed");
        }
    }

    public void rollback() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
            logger.debug("Transaction rolled back");
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