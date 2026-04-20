package com.appbana.util;

import com.appbana.AppManager;
import com.appbana.model.AppMetadata;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.Map;

public class BatchApplyMetadata {
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File("app_metadata_fixed.json");
        Map<String, Object> appData = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
        
        String appId = (String) appData.get("id");
        String tenantId = (String) appData.get("tenantId");
        
        System.out.println("Applying metadata for app: " + appId + " (tenant: " + tenantId + ")");
        
        // 1. Update App Metadata
        AppMetadata app = mapper.convertValue(appData, AppMetadata.class);
        AppManager.updateApp(tenantId, appId, app);
        System.out.println("  App properties updated.");
        
        // 2. Update Pages
        List<Map<String, Object>> pagesData = (List<Map<String, Object>>) appData.get("pagesData");
        for (Map<String, Object> page : pagesData) {
            String pageId = (String) page.get("id");
            System.out.println("  Updating page: " + pageId);
            AppManager.savePage(tenantId, appId, pageId, page);
        }
        
        System.out.println("Batch update complete!");
    }
}
