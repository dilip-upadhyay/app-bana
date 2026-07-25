# AI Agent Implementation - User Stories

**Project**: Advanced Learning AI Agent for AppBana  
**Tech Stack**: OpenAI GPT-4, Qdrant (self-hosted), PostgreSQL, LitElement  
**Timeline**: 10 weeks  
**Team**: Backend + Frontend developers + AI agents

---

## Epic 1: RAG Foundation (Week 1-2)

### Story 1.1: Set Up Qdrant Vector Database

**As a** backend developer  
**I want to** set up a self-hosted Qdrant vector database  
**So that** we can store and search conversation embeddings

**Acceptance Criteria**:
- [ ] Qdrant installed and running on server (Docker or binary)
- [ ] Collection created: `conversations` with 1536 dimensions
- [ ] Collection created: `app_patterns` with 1536 dimensions
- [ ] Health check endpoint responding
- [ ] Connection pooling configured
- [ ] Basic CRUD operations tested

**Technical Details**:
```bash
# Docker setup
docker run -p 6333:6333 qdrant/qdrant

# Create collection
curl -X PUT 'http://localhost:6333/collections/conversations' \
  -H 'Content-Type: application/json' \
  -d '{
    "vectors": {
      "size": 1536,
      "distance": "Cosine"
    }
  }'
```

**Definition of Done**:
- Qdrant accessible at `localhost:6333`
- Collections created and verified
- Documentation updated with setup instructions

---

### Story 1.2: Implement Embedding Service

**As a** backend developer  
**I want to** create a service that converts text to embeddings using OpenAI  
**So that** we can store semantic representations of conversations

**Acceptance Criteria**:
- [ ] `EmbeddingService.java` created in `com.appbana.ai.rag` package
- [ ] Integration with OpenAI `text-embedding-3-small` API
- [ ] Batch processing for multiple texts (up to 100 at once)
- [ ] Caching layer for repeated queries (Guava Cache, 1 hour TTL)
- [ ] Error handling for API failures
- [ ] Rate limiting (3000 requests/minute)
- [ ] Unit tests with 90%+ coverage

**API Contract**:
```java
public class EmbeddingService {
    /**
     * Generate embedding for single text
     * @param text Input text (max 8000 tokens)
     * @return 1536-dimensional vector
     * @throws EmbeddingException if API fails
     */
    public float[] embed(String text) throws EmbeddingException;
    
    /**
     * Generate embeddings for multiple texts (batched)
     * @param texts List of input texts
     * @return List of 1536-dimensional vectors
     */
    public List<float[]> embedBatch(List<String> texts);
}
```

**Test Cases**:
```java
@Test
public void testEmbed_validText_returnsVector() {
    float[] vector = service.embed("Create a CRM app");
    assertEquals(1536, vector.length);
}

@Test
public void testEmbed_cachedText_returnsCachedResult() {
    service.embed("test");
    long start = System.currentTimeMillis();
    service.embed("test");  // Should be instant
    assertTrue(System.currentTimeMillis() - start < 10);
}

@Test(expected = EmbeddingException.class)
public void testEmbed_apiFailure_throwsException() {
    // Mock API failure
    service.embed("test");
}
```

**Definition of Done**:
- All tests passing
- Code reviewed and merged
- OpenAI API key configured in environment
- Cost monitoring enabled

---

### Story 1.3: Implement Vector Store Service

**As a** backend developer  
**I want to** create a service that stores and searches vectors in Qdrant  
**So that** we can perform semantic search on conversations

**Acceptance Criteria**:
- [ ] `VectorStoreService.java` created in `com.appbana.ai.rag` package
- [ ] Store vectors with metadata (userId, timestamp, intent)
- [ ] Semantic search with top-K results
- [ ] Hybrid search (keyword + semantic)
- [ ] Filter by user, date range, intent
- [ ] Delete by ID or user
- [ ] Connection retry logic
- [ ] Unit tests with mocked Qdrant client

**API Contract**:
```java
public class VectorStoreService {
    /**
     * Store conversation with embedding
     */
    public String store(ConversationEmbedding embedding);
    
    /**
     * Semantic search
     * @param query Search query
     * @param topK Number of results
     * @param filter Optional filters
     * @return List of similar conversations
     */
    public List<SearchResult> search(
        String query, 
        int topK, 
        SearchFilter filter
    );
    
    /**
     * Delete all data for user (GDPR compliance)
     */
    public void deleteByUser(String userId);
}

public record ConversationEmbedding(
    String id,
    float[] vector,
    String userId,
    String message,
    String response,
    String intent,
    long timestamp
) {}

public record SearchFilter(
    String userId,
    Long startTime,
    Long endTime,
    String intent
) {}
```

**Test Cases**:
```java
@Test
public void testStore_validEmbedding_returnsId() {
    String id = service.store(createTestEmbedding());
    assertNotNull(id);
}

@Test
public void testSearch_similarQuery_returnsRelevantResults() {
    service.store(embedding("Create a CRM"));
    List<SearchResult> results = service.search("Make a CRM", 5, null);
    assertTrue(results.get(0).score > 0.8);
}

@Test
public void testSearch_withFilter_returnsFilteredResults() {
    service.store(embedding("user1", "Create CRM"));
    service.store(embedding("user2", "Create Blog"));
    
    List<SearchResult> results = service.search(
        "Create app", 
        10, 
        new SearchFilter("user1", null, null, null)
    );
    
    assertEquals(1, results.size());
}
```

**Definition of Done**:
- All tests passing
- Integration test with real Qdrant instance
- Performance: search < 100ms for 10K vectors
- Documentation with examples

---

### Story 1.4: Implement Conversation Memory

**As a** backend developer  
**I want to** create a service that manages long-term conversation memory  
**So that** the AI can remember past interactions across sessions

**Acceptance Criteria**:
- [ ] `ConversationMemory.java` created in `com.appbana.ai.rag` package
- [ ] Store conversation with automatic embedding
- [ ] Retrieve relevant past conversations
- [ ] Time-based filtering (last 7 days, 30 days, all time)
- [ ] Pagination for large result sets
- [ ] Database schema created (`ai_conversations` table)
- [ ] Integration with `VectorStoreService` and `EmbeddingService`

**Database Schema**:
```sql
CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    session_id UUID NOT NULL,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    intent VARCHAR(100),
    feedback INT DEFAULT 0,  -- -1, 0, 1
    created_at TIMESTAMP DEFAULT NOW(),
    metadata JSONB,
    
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_session (session_id)
);
```

**API Contract**:
```java
public class ConversationMemory {
    /**
     * Store conversation (auto-generates embedding)
     */
    public UUID store(Conversation conversation);
    
    /**
     * Search past conversations semantically
     */
    public List<Conversation> search(
        String userId,
        String query,
        TimeRange timeRange,
        int topK
    );
    
    /**
     * Get conversation history for session
     */
    public List<Conversation> getSessionHistory(UUID sessionId);
    
    /**
     * Delete all conversations for user (GDPR)
     */
    public void deleteByUser(String userId);
}

public record Conversation(
    UUID id,
    String userId,
    UUID sessionId,
    String message,
    String response,
    String intent,
    int feedback,
    LocalDateTime createdAt,
    Map<String, Object> metadata
) {}
```

**Test Cases**:
```java
@Test
public void testStore_validConversation_storesInDbAndVector() {
    UUID id = memory.store(createTestConversation());
    
    // Verify in database
    Conversation stored = db.findById(id);
    assertNotNull(stored);
    
    // Verify in vector store
    List<SearchResult> results = vectorStore.search("test", 1, null);
    assertEquals(id.toString(), results.get(0).id);
}

@Test
public void testSearch_pastConversation_returnsRelevant() {
    memory.store(conversation("user1", "Create a CRM last week"));
    
    List<Conversation> results = memory.search(
        "user1",
        "app creation last week",
        TimeRange.LAST_30_DAYS,
        5
    );
    
    assertTrue(results.size() > 0);
    assertTrue(results.get(0).message.contains("CRM"));
}
```

**Definition of Done**:
- Database migration created and tested
- All tests passing
- Integration test with real DB + Qdrant
- GDPR deletion tested

---

## Epic 2: Learning & Intelligence (Week 3-4)

### Story 2.1: Implement Pattern Miner

**As a** backend developer  
**I want to** create a service that discovers common app patterns  
**So that** the AI can suggest proven structures to users

**Acceptance Criteria**:
- [ ] `PatternMiner.java` created in `com.appbana.ai.learning` package
- [ ] Analyze all apps to find common entity combinations
- [ ] Identify relationship patterns
- [ ] Calculate pattern success rate (% of users who kept the app)
- [ ] Store patterns in database with embeddings
- [ ] Scheduled job to run pattern mining daily
- [ ] Quality gates (min 5 occurrences, min 50% success rate)

**Database Schema**:
```sql
CREATE TABLE ai_app_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_name VARCHAR(255) NOT NULL,
    description TEXT,
    app_type VARCHAR(100),  -- CRM, Blog, etc.
    entities JSONB NOT NULL,
    relationships JSONB,
    pages JSONB,
    usage_count INT DEFAULT 0,
    success_rate FLOAT,  -- 0.0 to 1.0
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    INDEX idx_app_type (app_type),
    INDEX idx_usage (usage_count DESC)
);
```

**API Contract**:
```java
public class PatternMiner {
    /**
     * Discover patterns for app type
     * @param appType Type of app (CRM, Blog, etc.)
     * @param minOccurrences Minimum times pattern must appear
     * @return Discovered patterns sorted by usage
     */
    public List<AppPattern> discoverPatterns(
        String appType,
        int minOccurrences
    );
    
    /**
     * Get most successful pattern for app type
     */
    public Optional<AppPattern> getBestPattern(String appType);
    
    /**
     * Update pattern statistics
     */
    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void updatePatternStats();
}

public record AppPattern(
    UUID id,
    String name,
    String appType,
    List<EntityDef> entities,
    List<Relationship> relationships,
    int usageCount,
    float successRate
) {}
```

**Test Cases**:
```java
@Test
public void testDiscoverPatterns_crmApps_findsCrmPattern() {
    // Create 10 CRM apps with similar structure
    for (int i = 0; i < 10; i++) {
        createCrmApp();
    }
    
    List<AppPattern> patterns = miner.discoverPatterns("CRM", 5);
    
    assertTrue(patterns.size() > 0);
    AppPattern crm = patterns.get(0);
    assertTrue(crm.entities().stream()
        .anyMatch(e -> e.name().equals("Contact")));
}

@Test
public void testGetBestPattern_multiplePatterns_returnsHighestSuccess() {
    createPattern("CRM-A", 0.9f);
    createPattern("CRM-B", 0.7f);
    
    AppPattern best = miner.getBestPattern("CRM").get();
    assertEquals("CRM-A", best.name());
}
```

**Definition of Done**:
- Pattern mining algorithm implemented
- Scheduled job configured
- Database migration created
- Tests passing with sample data
- Performance: analyze 1000 apps in < 30 seconds

---

### Story 2.2: Implement User Preference Engine

**As a** backend developer  
**I want to** create a service that learns user preferences  
**So that** the AI can personalize responses per user

**Acceptance Criteria**:
- [ ] `UserPreferenceEngine.java` created in `com.appbana.ai.learning` package
- [ ] Track industry/domain from app types
- [ ] Learn entity naming preferences (Customer vs Client)
- [ ] Detect communication style (technical vs simple)
- [ ] Store rejected suggestions
- [ ] Update preferences incrementally
- [ ] Database schema created

**Database Schema**:
```sql
CREATE TABLE ai_user_preferences (
    user_id VARCHAR(255) PRIMARY KEY,
    industry VARCHAR(100),
    communication_style VARCHAR(50),  -- technical, simple, formal, casual
    entity_naming_preferences JSONB,  -- {"customer": "Client", "user": "Member"}
    preferred_templates JSONB,  -- ["CRM", "Blog"]
    rejected_suggestions JSONB,  -- [{"type": "entity", "name": "Deal"}]
    interaction_count INT DEFAULT 0,
    last_active TIMESTAMP DEFAULT NOW(),
    
    INDEX idx_industry (industry),
    INDEX idx_last_active (last_active DESC)
);
```

**API Contract**:
```java
public class UserPreferenceEngine {
    /**
     * Learn from user's app creation
     */
    public void learnFromApp(String userId, AppMetadata app);
    
    /**
     * Learn from user's entity naming choice
     */
    public void learnNaming(String userId, String standard, String preferred);
    
    /**
     * Record rejected suggestion
     */
    public void recordRejection(String userId, Suggestion suggestion);
    
    /**
     * Get user's preferences
     */
    public UserPreferences getPreferences(String userId);
    
    /**
     * Apply preferences to suggestion
     */
    public Suggestion personalize(String userId, Suggestion base);
}

public record UserPreferences(
    String industry,
    CommunicationStyle style,
    Map<String, String> entityNaming,
    List<String> preferredTemplates,
    List<Suggestion> rejectedSuggestions
) {}
```

**Test Cases**:
```java
@Test
public void testLearnNaming_consistentUsage_updatesPreference() {
    engine.learnNaming("user1", "customer", "Client");
    engine.learnNaming("user1", "customer", "Client");
    engine.learnNaming("user1", "customer", "Client");
    
    UserPreferences prefs = engine.getPreferences("user1");
    assertEquals("Client", prefs.entityNaming().get("customer"));
}

@Test
public void testPersonalize_withPreferences_appliesNaming() {
    engine.learnNaming("user1", "customer", "Client");
    
    Suggestion base = new Suggestion("Create Customer entity");
    Suggestion personalized = engine.personalize("user1", base);
    
    assertTrue(personalized.text().contains("Client"));
    assertFalse(personalized.text().contains("Customer"));
}
```

**Definition of Done**:
- All preference types implemented
- Database migration created
- Tests passing
- Privacy: preferences deletable on request

---

### Story 2.3: Implement Feedback Loop

**As a** backend developer  
**I want to** create a service that collects and processes user feedback  
**So that** the AI can improve based on user reactions

**Acceptance Criteria**:
- [ ] `FeedbackLoop.java` created in `com.appbana.ai.learning` package
- [ ] Record thumbs up/down on responses
- [ ] Track accepted vs rejected suggestions
- [ ] Store user corrections ("No, I meant...")
- [ ] Update vector store with negative examples
- [ ] Calculate feedback metrics
- [ ] Database schema created

**Database Schema**:
```sql
CREATE TABLE ai_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES ai_conversations(id),
    user_id VARCHAR(255) NOT NULL,
    rating INT,  -- -1 (thumbs down), 0 (neutral), 1 (thumbs up)
    correction TEXT,  -- What user said instead
    accepted BOOLEAN,  -- Did user accept the suggestion?
    created_at TIMESTAMP DEFAULT NOW(),
    
    INDEX idx_conversation (conversation_id),
    INDEX idx_user_rating (user_id, rating),
    INDEX idx_created (created_at DESC)
);
```

**API Contract**:
```java
public class FeedbackLoop {
    /**
     * Record feedback on response
     */
    public void recordFeedback(
        UUID conversationId,
        String userId,
        int rating,
        String correction
    );
    
    /**
     * Record suggestion acceptance
     */
    public void recordAcceptance(
        UUID conversationId,
        boolean accepted
    );
    
    /**
     * Get feedback metrics
     */
    public FeedbackMetrics getMetrics(TimeRange range);
    
    /**
     * Process negative feedback (update embeddings)
     */
    @Async
    public void processNegativeFeedback(UUID feedbackId);
}

public record FeedbackMetrics(
    int totalFeedback,
    float averageRating,
    float acceptanceRate,
    int thumbsUp,
    int thumbsDown
) {}
```

**Test Cases**:
```java
@Test
public void testRecordFeedback_thumbsDown_storesInDb() {
    UUID convId = UUID.randomUUID();
    feedback.recordFeedback(convId, "user1", -1, "Wrong suggestion");
    
    Feedback stored = db.getFeedback(convId);
    assertEquals(-1, stored.rating());
    assertEquals("Wrong suggestion", stored.correction());
}

@Test
public void testGetMetrics_multipleFeedback_calculatesCorrectly() {
    recordFeedback(1);   // thumbs up
    recordFeedback(1);   // thumbs up
    recordFeedback(-1);  // thumbs down
    
    FeedbackMetrics metrics = feedback.getMetrics(TimeRange.ALL_TIME);
    assertEquals(3, metrics.totalFeedback());
    assertEquals(0.33f, metrics.averageRating(), 0.01);
}
```

**Definition of Done**:
- Feedback recording working
- Metrics calculation accurate
- Async processing tested
- Database migration created

---

## Epic 3: Intelligent Dialogue (Week 5-6)

### Story 3.1: Implement Dialogue Manager

**As a** backend developer  
**I want to** create a service that manages multi-turn conversations  
**So that** the AI can handle complex dialogues naturally

**Acceptance Criteria**:
- [ ] `DialogueManager.java` created in `com.appbana.ai.dialogue` package
- [ ] State machine for conversation states
- [ ] Context window management (last 10 messages)
- [ ] Handle interruptions and topic changes
- [ ] Ask clarifying questions when needed
- [ ] Track conversation flow
- [ ] Unit tests for all states

**Conversation States**:
```java
public enum ConversationState {
    INITIAL,              // Fresh conversation
    GATHERING_INFO,       // Collecting requirements
    CONFIRMING_DETAILS,   // Showing plan, awaiting confirmation
    CUSTOMIZING,          // User customizing template
    CREATING,             // Creating app
    COMPLETED             // App created
}
```

**API Contract**:
```java
public class DialogueManager {
    /**
     * Handle user message in conversation
     */
    public DialogueResponse handle(
        String userId,
        UUID sessionId,
        String message,
        ConversationContext context
    );
    
    /**
     * Get current conversation state
     */
    public ConversationState getState(UUID sessionId);
    
    /**
     * Reset conversation
     */
    public void reset(UUID sessionId);
}

public record DialogueResponse(
    String message,
    ConversationState newState,
    List<QuickAction> quickActions,
    boolean awaitingConfirmation,
    Object data  // Template, app preview, etc.
) {}

public record ConversationContext(
    UUID sessionId,
    String userId,
    List<Message> history,
    ConversationState state,
    Map<String, Object> data
) {}
```

**Test Cases**:
```java
@Test
public void testHandle_initialMessage_movesToGatheringInfo() {
    DialogueResponse response = manager.handle(
        "user1", 
        sessionId, 
        "Create a CRM", 
        context
    );
    
    assertEquals(ConversationState.GATHERING_INFO, response.newState());
    assertTrue(response.message().contains("CRM"));
}

@Test
public void testHandle_confirmation_movesToCreating() {
    context.setState(ConversationState.CONFIRMING_DETAILS);
    
    DialogueResponse response = manager.handle(
        "user1", 
        sessionId, 
        "yes", 
        context
    );
    
    assertEquals(ConversationState.CREATING, response.newState());
}
```

**Definition of Done**:
- All conversation states implemented
- State transitions tested
- Context management working
- Error handling for invalid states

---

*[Continue with remaining stories...]*

---

## Story Estimation

| Epic | Stories | Story Points | Duration |
|------|---------|--------------|----------|
| Epic 1: RAG Foundation | 4 | 21 | 2 weeks |
| Epic 2: Learning | 3 | 13 | 2 weeks |
| Epic 3: Dialogue | 3 | 13 | 2 weeks |
| Epic 4: LLM Integration | 2 | 8 | 1 week |
| Epic 5: Frontend | 4 | 13 | 2 weeks |
| Epic 6: Testing & Polish | 2 | 8 | 1 week |
| **Total** | **18** | **76** | **10 weeks** |

---

## Development Guidelines

### Code Standards
- Java 21 features encouraged
- All public methods must have Javadoc
- Unit test coverage > 80%
- Integration tests for all services
- Use Lombok for boilerplate reduction

### Testing Requirements
- Unit tests with JUnit 5
- Mock external dependencies (OpenAI, Qdrant)
- Integration tests with Testcontainers
- Performance tests for search operations

### Documentation
- Update API documentation
- Add setup instructions
- Document configuration options
- Include example usage

### Review Process
- All PRs require 1 approval
- CI must pass (tests + linting)
- Performance benchmarks must not regress
- Security scan must pass
