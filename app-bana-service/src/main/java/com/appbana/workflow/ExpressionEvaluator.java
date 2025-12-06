package com.appbana.workflow;

import org.mvel2.MVEL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Expression Evaluator for Workflow Conditions
 * Uses MVEL for safe expression evaluation
 */
public class ExpressionEvaluator {
    private static final Logger log = LoggerFactory.getLogger(ExpressionEvaluator.class);
    
    /**
     * Evaluate boolean condition expression
     */
    public static boolean evaluateCondition(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }
        
        try {
            String cleanExpr = unwrapExpression(expression);
            Map<String, Object> enrichedContext = new HashMap<>(context);
            enrichedContext.put("NOW", LocalDateTime.now());
            
            Object result = MVEL.eval(cleanExpr, enrichedContext);
            
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                log.warn("Expression did not return boolean: {} -> {}", expression, result);
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to evaluate condition: {}", expression, e);
            return false;
        }
    }
    
    /**
     * Evaluate expression that returns a value
     */
    public static Object evaluateValue(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        
        try {
            String cleanExpr = unwrapExpression(expression);
            Map<String, Object> enrichedContext = new HashMap<>(context);
            enrichedContext.put("NOW", LocalDateTime.now());
            
            return MVEL.eval(cleanExpr, enrichedContext);
        } catch (Exception e) {
            log.error("Failed to evaluate value expression: {}", expression, e);
            return null;
        }
    }
    
    /**
     * Remove ${} wrapper from expression
     */
    private static String unwrapExpression(String expression) {
        String trimmed = expression.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1);
        }
        return trimmed;
    }
    
    /**
     * Create context map from entity data
     */
    public static Map<String, Object> createContext(String entityType, Map<String, Object> entityData) {
        Map<String, Object> context = new HashMap<>();
        if (entityType != null && entityData != null) {
            context.put(entityType, entityData);
        }
        return context;
    }
}
