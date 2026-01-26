package com.scb.mrp.schemaflow.dbdeploy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scb.mrp.schemaflow.dbdeploy.entity.DatabaseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseStatusPrinter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void printStatus(DatabaseStatus status) {
        try {
            String jsonStatus = objectMapper.writeValueAsString(status);
            log.info("=== Database Deployment Status ===");
            log.info("\n{}", jsonStatus);
            log.info("=== End Status ===");
        } catch (Exception e) {
            log.error("Failed to serialize database status to JSON", e);
        }
    }
}