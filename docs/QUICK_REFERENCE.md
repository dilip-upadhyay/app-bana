# 🚀 QUICK REFERENCE - Entity Form Binding Implementation

**Print this or keep it open in a separate window**

---

## ⚡ Before Starting ANY Work (5 min)

```bash
cd /Users/dilipupadhyay/github/app-bana
./check_status.sh  # Or run script from SESSION_RESUME_GUIDE.md
```

**What it tells you:**
- ✅ Which stories are complete
- ❌ Which files are missing
- 🧪 Test status (passing/failing)
- 📦 Dependencies installed or not
- → Recommended next action

---

## 📋 Story Progress Checklist

| Story | Files | Tests | Status |
|-------|-------|-------|--------|
| **1.1 Password** | PasswordService.java<br>PasswordServiceTest.java | 6 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **1.2 CSRF** | CsrfService.java<br>SecurityMiddleware.java<br>CsrfServiceTest.java<br>SecurityMiddlewareTest.java | 5 + 4 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **1.3 Rate Limit** | RateLimitService.java<br>RateLimitMiddleware.java<br>Tests | 6 + 2 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **1.4 Validation** | ValidationService.java<br>FormComponent updates<br>Tests | 5 + 2 + 5 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **1.5 A11y** | ARIA attributes<br>E2E tests | 6 + 4 E2E | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **2.1 Loading** | FormComponent loading UI | 6 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **2.2 Progressive** | Validation strategy | 6 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **3.1 Transactions** | TransactionService.java | 3 + 1 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |
| **3.2 File Upload** | FileUploadService.java<br>FileUpload component | 4 + 5 tests | ⬜ Not Started<br>⬜ In Progress<br>⬜ Complete |

---

## 🎯 Decision Matrix

| If you see... | Then... |
|--------------|---------|
| ❌ **PasswordService.java missing** | Start Day 1 → `docs/START_HERE.md` |
| ✅ **PasswordService exists, 6 tests pass** | Skip to Story 1.2 → `docs/ENTITY_FORM_BINDING_TEST_PLAN.md` |
| ⚠️ **Tests run: 6, Failures: 3** | Debug tests first (don't continue) |
| ❌ **BCrypt missing** | Run `mvn clean install` first |
| ✅ **CsrfService exists** | Working on Story 1.2, continue |
| ✅ **Multiple stories started** | Fix any failing tests before new story |

---

## 🧪 Quick Test Commands

```bash
# Test specific story
mvn test -Dtest=PasswordServiceTest   # Story 1.1
mvn test -Dtest=CsrfServiceTest       # Story 1.2
mvn test -Dtest=RateLimitServiceTest  # Story 1.3

# Run all backend tests
cd app-bana-service && mvn test

# Run all frontend tests
cd app-bana-ui && npm test

# Check coverage
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

---

## 📚 Documentation Quick Links

| Document | When to Use |
|----------|-------------|
| **SESSION_RESUME_GUIDE.md** | Every new session (FIRST!) |
| **START_HERE.md** | Day 1 implementation (Story 1.1) |
| **ENTITY_FORM_BINDING_TEST_PLAN.md** | Copy test code for any story |
| **ENTITY_FORM_BINDING_STORIES.md** | Understand user stories/acceptance criteria |
| **IMPLEMENTATION_FILE_STRUCTURE.md** | See complete file structure (36 files) |

---

## 🚨 Common Mistakes to Avoid

❌ **DON'T:** Start coding without running status check  
✅ **DO:** Run `check_status.sh` first

❌ **DON'T:** Recreate files that already exist  
✅ **DO:** Check `find . -name "PasswordService.java"` first

❌ **DON'T:** Continue with failing tests  
✅ **DO:** Fix all tests before moving to next story

❌ **DON'T:** Skip dependencies  
✅ **DO:** Install BCrypt, JUnit, @testing-library/lit first

❌ **DON'T:** Code without tests  
✅ **DO:** Follow test-first approach (copy from TEST_PLAN.md)

---

## 💡 Pro Tips

1. **Commit after each story**
   ```bash
   git add .
   git commit -m "Story 1.1 complete - 6 tests pass"
   ```

2. **Use branches for stories**
   ```bash
   git checkout -b story-1.2-csrf
   ```

3. **Keep notes.txt updated**
   ```bash
   echo "Story 1.2 in progress - $(date)" >> notes.txt
   ```

4. **Run tests frequently**
   ```bash
   mvn test  # Every 15 minutes while coding
   ```

5. **Check coverage**
   ```bash
   # Goal: >80% coverage
   mvn clean test jacoco:report
   ```

---

## 📞 Help & Support

| Issue | Solution |
|-------|----------|
| **"I'm lost"** | Run `check_status.sh` |
| **"Tests failing"** | `mvn test -Dtest=PasswordServiceTest` and read output |
| **"Which story am I on?"** | Check output of `check_status.sh` |
| **"New AI session"** | Share `check_status.sh` output with AI agent |
| **"Dependencies missing"** | See `docs/START_HERE.md` Step 1 |
| **"Foundation files missing"** | See `docs/START_HERE.md` "What's Been Set Up" |

---

## ✅ Session Start Checklist

Every new session, check these boxes:

- [ ] Ran `check_status.sh` (5 min)
- [ ] Read recommendation at end of status output
- [ ] Identified current story (e.g., Story 1.2)
- [ ] Opened appropriate documentation
- [ ] Checked test status (passing or failing?)
- [ ] Ready to code!

---

**Keep this reference open while coding!**

**Last Updated:** December 30, 2025
