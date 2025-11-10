# AI Chat Builder - Quick Testing Guide

**Date**: November 10, 2025  
**Version**: 2.0 Enhanced

## Prerequisites

1. **Backend Running**:
   ```powershell
   cd c:\Users\dilip\git\app-bana
   # If JAR exists:
   java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar
   
   # If need to rebuild:
   mvn clean package -DskipTests
   java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar
   ```

2. **Frontend Running**:
   ```powershell
   cd c:\Users\dilip\git\app-bana\app-bana-ui
   npm run dev
   ```

3. **AI Configuration**:
   - Open: `http://localhost:5173/studio.html`
   - Click "AI Builder" tab
   - Click ⚙️ Settings button
   - Configure AI provider (OpenAI recommended for follow-ups)
   - Enter API key
   - Test connection
   - Save

## Test Scenarios

### ✅ Test 1: Simple App (No Follow-ups)

**Prompt**: `Create a blog with posts and comments`

**Expected Results**:
- ✓ AI generates structure immediately (no questions)
- ✓ Shows preview with:
  - App name: "Blog Application"
  - 2 entities: Post, Comment
  - Post fields: title, content, author, publishedAt, status
  - Comment fields: content, author, postId
  - 3-5 pages (Posts List, Post Detail, Create Post)
- ✓ Click "Create This App" works
- ✓ App created with entities AND pages
- ✓ Success message: "Application 'Blog Application' created successfully! • 2 entities created • 3 pages created"

**Verification**:
```powershell
# Check app exists
curl http://localhost:8080/apps

# Check app details (replace {appId})
curl http://localhost:8080/apps/{appId}
```

---

### ✅ Test 2: Complex Request (Follow-ups Expected)

**Prompt**: `Build an e-commerce store`

**Expected Results**:
- ✓ AI asks 3-5 clarifying questions like:
  - "What specific fields should the Product entity have?"
  - "Do you need user authentication?"
  - "Should orders track shipping information?"
- ✓ Answer questions naturally
- ✓ AI generates detailed structure based on answers
- ✓ Shows preview with 3-5 entities and 5-7 pages
- ✓ Can create app successfully

**Test Answer**: `Products need name, price, description, and image. Yes to auth with email/password. Yes to shipping with address and tracking.`

---

### ✅ Test 3: Modification Request

**Prompt**: `Create a task manager`

**Steps**:
1. Wait for preview
2. Click "✎ Request Changes" button
3. Input: `add categories and priorities to tasks`
4. Wait for updated preview
5. Verify AI added category/priority fields
6. Create app

**Expected**: Updated preview shows modified structure

---

### ✅ Test 4: Vague Request (Multiple Follow-ups)

**Prompt**: `I need an app for my business`

**Expected Results**:
- ✓ AI asks about business type
- ✓ AI asks what you want to track
- ✓ AI asks about user roles/permissions
- ✓ Interactive conversation to clarify
- ✓ Eventually generates structure
- ✓ Can create app

---

### ✅ Test 5: Page Verification

**After creating any app**:

1. **Check App in Studio**:
   - Switch to "Apps" tab
   - Verify app appears in list
   - Click app name
   - Verify entities appear in "Entities" section
   - Click "Pages" tab
   - Verify pages appear

2. **Check Backend**:
   ```powershell
   # List apps
   curl http://localhost:8080/apps
   
   # Get app details
   curl http://localhost:8080/apps/{appId}
   # Should show: "pages": ["page-1", "page-2", ...]
   
   # Get specific page
   curl http://localhost:8080/apps/{appId}/pages/{pageId}
   # Should show PageMeta with nodes
   ```

3. **Check File System**:
   ```powershell
   # Check app directory
   ls apps/{appId}/
   # Should show: app.json, pages/ directory
   
   # Check pages directory
   ls apps/{appId}/pages/
   # Should show: {pageId}.json files
   ```

---

## Common Issues

### Issue 1: AI Not Asking Questions
**Symptom**: Always generates immediately, never asks follow-ups  
**Cause**: Using GPT-3.5 or insufficient prompt understanding  
**Fix**: 
- Use GPT-4o or GPT-4o-mini (better instruction following)
- Use Anthropic Claude 3.5 Sonnet
- Try more vague prompts like "build an app" or "I need something for inventory"

### Issue 2: Pages Not Created
**Symptom**: Success message but no pages in app  
**Cause**: Page creation failed silently  
**Debug**:
```javascript
// Browser console
1. Check for errors in console
2. Check Network tab for failed API calls
3. Look for [AiChatBuilder] logs
```

**Check Backend**:
```powershell
# Backend logs should show:
# "Adding page to app: {appId}, page: {pageName}"
```

### Issue 3: Invalid JSON from AI
**Symptom**: Error message "Failed to generate app structure"  
**Cause**: AI returned malformed JSON  
**Fix**:
- Check backend logs for JSON parse errors
- Try rephrasing prompt
- Try simpler request first
- Verify AI configuration

### Issue 4: Preview Not Showing Details
**Symptom**: Preview shows entities but no field details  
**Cause**: AI response missing fields array  
**Debug**:
```javascript
// Browser console - check message metadata
const lastMessage = // Find last assistant message
console.log(lastMessage.metadata.generatedEntities)
// Should show array with fields
```

---

## Success Checklist

For each test, verify:

- [ ] AI responds appropriately (questions or structure)
- [ ] Preview shows detailed information (entities with fields, pages with types)
- [ ] Both buttons work ("Create This App" and "Request Changes")
- [ ] App creation succeeds
- [ ] Success message shows correct counts
- [ ] App appears in Apps tab
- [ ] Entities present in app
- [ ] **Pages present in app** (this is the new feature!)
- [ ] Pages viewable in Studio
- [ ] Backend files exist (app.json, pages/*.json)

---

## Testing Commands Reference

### PowerShell Commands

```powershell
# Check backend running
Get-NetTCPConnection -LocalPort 8080 -State Listen

# Kill backend if needed
Get-NetTCPConnection -LocalPort 8080 | Select-Object -ExpandProperty OwningProcess | Stop-Process -Force

# Start backend
cd c:\Users\dilip\git\app-bana
java -jar app-bana-service/target/app-bana-service-1.0-SNAPSHOT.jar

# Start frontend
cd c:\Users\dilip\git\app-bana\app-bana-ui
npm run dev

# Check apps via API
Invoke-WebRequest -Uri http://localhost:8080/apps | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 10

# Check specific app
Invoke-WebRequest -Uri http://localhost:8080/apps/{appId} | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 10

# Check app's pages
ls apps/{appId}/pages/
```

### Browser Console Debugging

```javascript
// Check AppStore state
console.log(appStore.getCurrentApp())

// Check messages in AI chat
// Find AiChatBuilder instance and inspect messages

// Check for page creation calls
// Look for logs: [AiChatBuilder] Created page: ...
```

---

## Expected Performance

| Metric | Target | Actual |
|--------|--------|--------|
| Simple app generation | < 5 seconds | TBD |
| Follow-up conversation | 2-4 questions | TBD |
| Total time to app | < 2 minutes | TBD |
| Success rate | > 90% | TBD |
| Pages created per app | 3-7 pages | TBD |

---

## Next Steps After Testing

1. **Document Results**: Note what works, what doesn't
2. **Report Issues**: File issues for any bugs found
3. **Suggest Improvements**: Ideas for better prompts, better UI
4. **Performance Tuning**: Optimize slow areas
5. **User Testing**: Get feedback from real users

---

**Happy Testing!** 🚀

Report any issues or unexpected behavior.
