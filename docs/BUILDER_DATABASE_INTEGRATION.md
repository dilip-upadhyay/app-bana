# Builder Database Integration - AI Prompt Enhancement

**Date**: November 10, 2025  
**Status**: ✅ Complete  
**Version**: Dynamic Builder Database Integration

## Overview

Enhanced the AI Chat Builder to **dynamically reference the builder-database** as the authoritative source of AppBana capabilities. Instead of hardcoding field types, page templates, and components in the AI prompt, the system now:

1. **Loads capabilities from builder-database JSON files at runtime**
2. **Injects comprehensive, up-to-date information into AI prompts**
3. **Ensures AI always knows the latest platform capabilities**
4. **Eliminates manual prompt updates when features are added**

## What Changed

### Before (Hardcoded Prompt)
```java
public static final String APP_GENERATION_PROMPT = """
### Field Types (38 available):
**Basic Types**: text, longtext, number...
**Contact**: email, phone, url...
// etc - all hardcoded
""";
```

**Problems**:
- ❌ Manual updates required when adding field types
- ❌ Prompt could get out of sync with codebase
- ❌ No reference to actual implementation
- ❌ Limited detail (only summaries)

### After (Dynamic Builder Database)
```java
public static String getAppGenerationPrompt() {
    // Load from builder-database files
    String indexContent = loadBuilderDatabaseFile("99-capabilities-index.json");
    String entitiesContent = loadBuilderDatabaseFile("03-entities.json");
    String pagesContent = loadBuilderDatabaseFile("04-pages.json");
    String componentsContent = loadBuilderDatabaseFile("02-components.json");
    
    // Parse and format
    JsonNode entities = mapper.readTree(entitiesContent);
    formatFieldTypes(entities); // Detailed field type info
    
    // Inject into prompt
    return basePrompt + builderDatabaseContent + instructions;
}
```

**Benefits**:
- ✅ Automatic updates from builder-database
- ✅ Always in sync with actual capabilities
- ✅ Comprehensive detail (SQL types, descriptions, validation)
- ✅ Single source of truth

## Implementation Details

### New Method Structure

**File**: `app-bana-service/src/main/java/com/appbana/ai/AiSystemPrompts.java`

```java
public class AiSystemPrompts {
    
    // Main method - loads and constructs prompt dynamically
    public static String getAppGenerationPrompt() {
        // Loads from builder-database/
        // Formats and injects capabilities
        // Returns complete prompt
    }
    
    // Helper methods
    private static String loadBuilderDatabaseFile(String filename) {...}
    private static String formatCapabilitiesSummary(JsonNode index) {...}
    private static String formatFieldTypes(JsonNode entities) {...}
    private static String formatPageTemplates(JsonNode pages) {...}
    private static String formatComponentsSummary(JsonNode components) {...}
    
    // Static prompt parts
    private static final String BASE_APP_GENERATION_PROMPT = "...";
    private static final String GENERATION_INSTRUCTIONS = "...";
    
    // Legacy for compatibility
    @Deprecated(since = "2.0")
    public static final String APP_GENERATION_PROMPT = ...;
}
```

### Builder Database Files Loaded

1. **99-capabilities-index.json**
   - Quick reference summary
   - Total counts (components, field types, templates, datasources)
   - Injected as: "Quick Reference Summary"

2. **03-entities.json**
   - All 38 field types with categories
   - SQL mappings
   - Descriptions and validation rules
   - Injected as: "Complete Field Types Reference"
   - **Most important for accurate generation**

3. **04-pages.json**
   - 7 page templates
   - Descriptions and use cases
   - Injected as: "Available Page Templates"

4. **02-components.json**
   - All UI components
   - Categories and common usage
   - Injected as: "Available UI Components"

### Example Generated Prompt Section

```
## COMPREHENSIVE CAPABILITY REFERENCE

The following is extracted from AppBana's builder-database - the authoritative source:

### Quick Reference Summary:
- **Total Components**: 14
- **Total Field Types**: 38
- **Total Page Templates**: 7
- **Total Datasources**: 25

### Complete Field Types Reference:
**Text Types**:
  - `text`: Short text - names, titles (SQL: VARCHAR(255))
  - `longtext`: Long text - descriptions, notes (SQL: TEXT)
  - `email`: Email with validation (SQL: VARCHAR(255))
  - `phone`: Phone number with formatting (SQL: VARCHAR(20))
  - `url`: URL with validation (SQL: VARCHAR(500))
  - `color`: Color picker (#RRGGBB) (SQL: VARCHAR(7))

**Numeric Types**:
  - `number`: Integer (SQL: BIGINT)
  - `decimal`: Decimal/float (SQL: DECIMAL(19,4))
  - `currency`: Money amount (SQL: DECIMAL(19,4))
  - `percentage`: Percentage (0-100) (SQL: DECIMAL(5,2))

... [continues with all categories]

### Available Page Templates:
- **Login Page**: User login form with email/password
- **Dashboard Page**: Overview dashboard with metrics cards
- **Contact Form**: Contact form with name, email, message fields
- **Landing Page**: Marketing landing page with hero section
- **Profile Page**: User profile with avatar and info
- **Data Table**: Tabular data display with columns
- **Blank Canvas**: Empty page with single root container

### Available UI Components:
Available in categories: Layout Components, Form Components, Display Components, Navigation Components, Data Components

Most commonly used:
  - `container`: Generic container for grouping child components with layout control
  - `button`: Interactive button with variants and sizes
  - `text`: Text display with formatting options
  - `app-grid`: Responsive grid layout
  - `app-form`: Form with validation
  - `app-input`: Text input field
  - `app-select`: Dropdown selection
  - `app-checkbox`: Checkbox input
  - `app-table`: Data table
  - `app-card`: Card container

... [continues]
```

### Path Resolution

```java
private static String loadBuilderDatabaseFile(String filename) {
    // Try from service directory: builder-database/
    Path filePath = Paths.get(BUILDER_DB_PATH, filename);
    
    if (!Files.exists(filePath)) {
        // Try from parent: ../builder-database/
        filePath = Paths.get("..", BUILDER_DB_PATH, filename);
    }
    
    if (Files.exists(filePath)) {
        return Files.readString(filePath);
    }
    
    LOG.warn("Builder database file not found: {}", filename);
    return null;
}
```

Works in both:
- Development: When running from `app-bana-service/` directory
- Production: When running from JAR in parent directory

## Usage

### Updated Service Call

**File**: `app-bana-service/src/main/java/com/appbana/AiAppGeneratorService.java`

```java
private static GenerationResult generateWithAi(...) {
    AiProvider provider = AiProviderFactory.createProvider(config);
    
    // OLD: String systemPrompt = AiSystemPrompts.APP_GENERATION_PROMPT;
    // NEW: Load with builder-database integration
    String systemPrompt = AiSystemPrompts.getAppGenerationPrompt();
    
    String jsonResponse = provider.generateAppStructure(userPrompt, systemPrompt);
    return parseAiResponse(jsonResponse);
}
```

### Backward Compatibility

```java
@Deprecated(since = "2.0", forRemoval = false)
public static final String APP_GENERATION_PROMPT = 
    BASE_APP_GENERATION_PROMPT + 
    "\n\n[Builder database not loaded - using base prompt]\n\n" + 
    GENERATION_INSTRUCTIONS;
```

Legacy code still works but uses base prompt without builder-database enrichment.

## Benefits

### 1. Always Up-to-Date
When you add a new field type to `03-entities.json`:
```json
{
  "type": "geolocation",
  "sqlType": "POINT",
  "description": "GPS coordinates",
  "validation": ["latitude", "longitude"]
}
```

**No code changes needed** - AI automatically knows about it on next restart.

### 2. Comprehensive Information
AI now sees:
- **38 field types** with SQL mappings and descriptions
- **7 page templates** with detailed descriptions
- **14+ components** with categories
- **Validation rules** for each field type
- **Display properties** and formatting

### 3. Single Source of Truth
```
builder-database/*.json (single source)
    ↓
AI Prompt (runtime)
    ↓
AI Generation (accurate)
    ↓
App Creation (valid)
```

No duplicate documentation, no sync issues.

### 4. Better AI Accuracy
With detailed field type info, AI makes better choices:
- "email address" → Uses `email` type (not `text`)
- "price" → Uses `currency` type (not `decimal`)
- "long description" → Uses `longtext` type (not `text`)
- "status dropdown" → Uses `status` type (not `text`)

## Testing

### Verify Builder Database Loading

```bash
# Start backend with logging
cd app-bana-service
mvn clean package -DskipTests
java -jar target/app-bana-service-1.0-SNAPSHOT.jar
```

**Look for logs**:
```
[INFO] Successfully loaded builder database content into AI prompt
```

Or:
```
[WARN] Failed to load builder database content, using base prompt only
```

### Test AI Generation

1. **Open AI Chat Builder**: `http://localhost:5173/studio.html`
2. **Configure AI**: Settings → OpenAI/Anthropic → Save
3. **Test Prompt**: "Create an e-commerce store with products and orders"
4. **Verify**:
   - AI suggests appropriate field types (currency for price, etc.)
   - Page suggestions are accurate
   - Entities use correct field types from builder-database

### Verify Field Type Usage

After generation, check created entities:
```bash
curl http://localhost:8080/apps/{appId}
```

Look for entities with:
- `currency` fields for prices
- `email` fields for email addresses
- `datetime` fields for timestamps
- `longtext` fields for descriptions
- `status` fields for dropdowns

## Fallback Behavior

If builder-database files are not found:

1. **Logs warning**: `Builder database file not found: 03-entities.json`
2. **Continues with base prompt**: Still functional but less detailed
3. **No crash**: System remains operational

**Base prompt includes**:
- Summary of field types (38 types listed)
- Summary of page templates (7 templates listed)
- Basic relationship types
- Generation instructions

But without detailed SQL mappings, descriptions, and validation rules.

## Maintenance

### When Adding New Field Type

1. **Update builder-database**:
   ```json
   // builder-database/03-entities.json
   {
     "category": "New Category",
     "fields": [
       {
         "type": "newtype",
         "sqlType": "...",
         "description": "...",
         "validation": [...]
       }
     ]
   }
   ```

2. **Restart backend** - That's it! AI now knows about it.

3. **No code changes needed** in `AiSystemPrompts.java`

### When Adding New Page Template

1. **Update builder-database**:
   ```json
   // builder-database/04-pages.json
   {
     "name": "New Template",
     "description": "...",
     "file": "...",
     "method": "...",
     "components": [...]
   }
   ```

2. **Implement in PageManager** (frontend)

3. **Restart backend** - AI knows to suggest it

## Files Modified

- ✅ `app-bana-service/src/main/java/com/appbana/ai/AiSystemPrompts.java` - Complete rewrite with dynamic loading
- ✅ `app-bana-service/src/main/java/com/appbana/AiAppGeneratorService.java` - Updated to use new method

## Future Enhancements

### Potential Improvements

1. **Cache Loaded Content**: Don't reload on every request
2. **Hot Reload**: Watch builder-database files for changes
3. **Validation**: Validate JSON schema on load
4. **Error Recovery**: Better fallback if specific files fail
5. **Metrics**: Track which capabilities AI uses most

### Example Caching

```java
private static String cachedPrompt = null;
private static long lastLoadTime = 0;
private static final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

public static String getAppGenerationPrompt() {
    long now = System.currentTimeMillis();
    if (cachedPrompt != null && (now - lastLoadTime) < CACHE_DURATION) {
        return cachedPrompt;
    }
    
    // Load and cache
    cachedPrompt = buildPrompt();
    lastLoadTime = now;
    return cachedPrompt;
}
```

## Conclusion

The AI Chat Builder now **dynamically references the builder-database** as the authoritative source of AppBana capabilities. This ensures:

- ✅ AI prompts are always accurate and up-to-date
- ✅ No manual updates when adding features
- ✅ Comprehensive field type information for better AI decisions
- ✅ Single source of truth (builder-database)
- ✅ Easy maintenance (update JSON, restart server)

The system is **production-ready** with proper fallback behavior and backward compatibility.

---

**Key Insight**: By making the AI prompt dynamic and sourced from builder-database, we've eliminated a major maintenance burden and ensured the AI always has complete, accurate knowledge of the platform's capabilities. 🎯
