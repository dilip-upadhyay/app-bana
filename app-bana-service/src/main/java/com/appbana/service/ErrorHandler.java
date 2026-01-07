package com.appbana.service;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error handling and formatting service
 */
public class ErrorHandler {

    /**
     * Format exception into standard error response
     * Preserves SQL error codes and messages for debugging
     */
    public static Map<String, Object> errorDetails(Throwable ce) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", ce.getMessage() != null ? ce.getMessage() : ce.getClass().getSimpleName());

        if (ce instanceof SQLException) {
            SQLException sqe = (SQLException) ce;
            m.put("sqlState", sqe.getSQLState());
            m.put("errorCode", sqe.getErrorCode());
        }

        return m;
    }
}
