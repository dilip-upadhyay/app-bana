package com.appbana.server.routes;

import com.appbana.api.Router;
import com.appbana.workflow.api.WorkflowApi;

/**
 * Workflow and task management routes
 */
public class WorkflowRoutes {

    public static void register(Router router) {
        // Initialize workflow engine
        WorkflowApi.initialize();

        // Workflow CRUD
        router.post("/api/workflows", WorkflowApi.createOrUpdateWorkflow());
        router.get("/api/workflows", WorkflowApi.listWorkflows());
        router.get("/api/workflows/{id}", WorkflowApi.getWorkflow());
        router.post("/api/workflows/{id}/publish", WorkflowApi.publishWorkflow());
        router.post("/api/workflows/{id}/start", WorkflowApi.startWorkflow());

        // Task management
        router.get("/api/my-tasks", WorkflowApi.getMyTasks());
        router.post("/api/my-tasks/{tokenId}/complete", WorkflowApi.completeTask());
        router.get("/api/my-requests", WorkflowApi.getMyRequests());
        router.get("/api/workflow-instances", WorkflowApi.listInstances());
    }
}
