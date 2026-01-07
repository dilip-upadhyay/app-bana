# User Authentication & RBAC - Design Document

**Date**: November 22, 2025  
**Status**: In Progress  
**Priority**: P0 - Critical for Enterprise

---

## 🎯 Goals

1. **Multi-user support** - Multiple users can access the same app
2. **Secure authentication** - Email/password with BCrypt hashing + JWT tokens
3. **Role-based access control** - Admin, Manager, User roles with permissions
4. **Field-level security** - Hide/show fields based on role
5. **Record-level security** - Users see only their own records or shared records
6. **Audit trail** - Track who did what and when

---

## 📊 Database Schema

### Entity Relationship Diagram
```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│    User     │────────▶│  UserRole   │◀────────│    Role     │
│             │  many   │  (junction) │  many   │             │
├─────────────┤         ├─────────────┤         ├─────────────┤
│ id          │         │ userId      │         │ id          │
│ email       │         │ roleId      │         │ name        │
│ passwordHash│         │ assignedAt  │         │ description │
│ name        │         │ assignedBy  │         │ createdAt   │
│ status      │         └─────────────┘         └─────────────┘
│ createdAt   │                                        │
│ lastLogin   │                                        │ many
└─────────────┘                                        ▼
                                              ┌─────────────────┐
                                              │ RolePermission  │
                                              │   (junction)    │
                                              ├─────────────────┤
                                              │ roleId          │
                                              │ permissionId    │
                                              └─────────────────┘
                                                       │
                                                       │ many
                                                       ▼
                                              ┌─────────────────┐
                                              │   Permission    │
                                              ├─────────────────┤
                                              │ id              │
                                              │ resource        │
                                              │ action          │
                                              │ scope           │
                                              └─────────────────┘
```

### Table Definitions

#### **1. User Table**
```sql
CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'active',  -- active, inactive, locked
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_email (email),
    INDEX idx_status (status)
);
```

#### **2. Role Table**
```sql
CREATE TABLE role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_system BOOLEAN DEFAULT FALSE,  -- System roles can't be deleted
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_name (name)
);

-- Default system roles
INSERT INTO role (name, description, is_system) VALUES
('admin', 'Full system access', true),
('manager', 'Manage resources within scope', true),
('user', 'Basic user access', true);
```

#### **3. UserRole Table (Junction)**
```sql
CREATE TABLE user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NULL,
    
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_by) REFERENCES app_user(id) ON DELETE SET NULL,
    
    UNIQUE KEY unique_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
);
```

#### **4. Permission Table**
```sql
CREATE TABLE permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource VARCHAR(100) NOT NULL,  -- Entity name or 'system'
    action VARCHAR(50) NOT NULL,     -- create, read, update, delete, *
    scope VARCHAR(50) NOT NULL,      -- all, own, none
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY unique_permission (resource, action, scope),
    INDEX idx_resource (resource)
);

-- Examples:
-- ('Project', 'read', 'all')   - Read all projects
-- ('Project', 'update', 'own') - Update own projects only
-- ('Project', 'delete', 'none') - Cannot delete projects
-- ('*', '*', 'all')             - Full access to everything
```

#### **5. RolePermission Table (Junction)**
```sql
CREATE TABLE role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE,
    
    UNIQUE KEY unique_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
);
```

---

## 🔐 Authentication Flow

### Registration Flow
```
1. User submits registration form
   POST /api/auth/register
   Body: { email, password, name }
   
2. Backend validates:
   ✓ Email format valid
   ✓ Email not already registered
   ✓ Password meets requirements (8+ chars, uppercase, lowercase, digit)
   
3. Backend hashes password with BCrypt (cost factor: 12)
   passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12))
   
4. Backend creates user record:
   INSERT INTO app_user (email, password_hash, name, status)
   VALUES (email, passwordHash, name, 'active')
   
5. Backend assigns default 'user' role:
   INSERT INTO user_role (user_id, role_id)
   SELECT <new_user_id>, id FROM role WHERE name = 'user'
   
6. Backend generates JWT token:
   token = JWT.sign({
     userId: user.id,
     email: user.email,
     roles: ['user']
   }, secretKey, { expiresIn: '7d' })
   
7. Backend returns:
   Response: {
     success: true,
     user: { id, email, name, roles: ['user'] },
     token: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   }
   
8. Frontend stores token in localStorage
   localStorage.setItem('authToken', token)
   
9. Frontend updates AuthStore
   authStore.setUser(user)
   
10. Frontend redirects to app
    window.location.href = '/apps'
```

### Login Flow
```
1. User submits login form
   POST /api/auth/login
   Body: { email, password }
   
2. Backend finds user by email:
   SELECT * FROM app_user WHERE email = :email
   
3. Backend verifies password:
   isValid = BCrypt.checkpw(password, user.password_hash)
   
4. If invalid:
   Response: { success: false, error: "Invalid credentials" }
   
5. If valid, check status:
   if (user.status !== 'active'):
     Response: { success: false, error: "Account is inactive" }
   
6. Backend loads user roles:
   SELECT r.name FROM role r
   JOIN user_role ur ON ur.role_id = r.id
   WHERE ur.user_id = :userId
   
7. Backend updates last_login:
   UPDATE app_user SET last_login = NOW() WHERE id = :userId
   
8. Backend generates JWT token with roles:
   token = JWT.sign({
     userId: user.id,
     email: user.email,
     name: user.name,
     roles: ['admin', 'manager']
   }, secretKey, { expiresIn: '7d' })
   
9. Backend returns user + token
10. Frontend stores token
11. Frontend updates AuthStore
12. Frontend redirects to app
```

### Token Validation (Middleware)
```
1. Frontend makes API request with token:
   GET /api/apps/myapp/data/Project
   Headers: { Authorization: "Bearer <token>" }
   
2. Backend intercepts request:
   AuthenticationFilter.doFilter(request, response)
   
3. Backend extracts token:
   token = request.getHeader("Authorization").replace("Bearer ", "")
   
4. Backend validates token:
   try {
     claims = JWT.verify(token, secretKey)
     userId = claims.userId
     roles = claims.roles
   } catch (JWTExpiredException) {
     Response: 401 Unauthorized - "Token expired"
   } catch (JWTVerificationException) {
     Response: 401 Unauthorized - "Invalid token"
   }
   
5. Backend loads fresh user data:
   user = findUserById(userId)
   if (user.status !== 'active'):
     Response: 401 Unauthorized - "Account inactive"
   
6. Backend attaches user to request context:
   request.setAttribute("currentUser", user)
   request.setAttribute("currentRoles", roles)
   
7. Backend continues to actual handler:
   chain.doFilter(request, response)
```

### Authorization Check (Permission)
```
1. User requests to delete a project:
   DELETE /api/apps/myapp/data/Project/123
   
2. Handler checks permission:
   currentUser = request.getAttribute("currentUser")
   currentRoles = request.getAttribute("currentRoles")
   
3. Load permissions for user's roles:
   permissions = SELECT p.* FROM permission p
     JOIN role_permission rp ON rp.permission_id = p.id
     JOIN user_role ur ON ur.role_id = rp.role_id
     WHERE ur.user_id = :userId
       AND p.resource IN ('Project', '*')
       AND p.action IN ('delete', '*')
   
4. Check if permission exists:
   hasPermission = permissions.any(p => 
     (p.scope === 'all') ||
     (p.scope === 'own' && project.createdBy === currentUser.id)
   )
   
5. If no permission:
   Response: 403 Forbidden - "You don't have permission to delete projects"
   
6. If has permission:
   DELETE FROM project WHERE id = 123
   Response: 200 OK
```

---

## 🎨 Frontend Components

### **1. LoginForm.ts**
```typescript
import { LitElement, html, css } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { authStore } from '../store/AuthStore';

@customElement('login-form')
export class LoginForm extends LitElement {
  @state() email = '';
  @state() password = '';
  @state() error = '';
  @state() loading = false;

  static styles = css`
    .login-container {
      max-width: 400px;
      margin: 100px auto;
      padding: 32px;
      border: 1px solid #ddd;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
    h2 { margin-top: 0; }
    .error { color: red; margin-bottom: 16px; }
    button { width: 100%; margin-top: 16px; }
  `;

  render() {
    return html`
      <div class="login-container">
        <h2>Login to AppBana</h2>
        
        ${this.error ? html`<div class="error">${this.error}</div>` : ''}
        
        <form @submit=${this.handleSubmit}>
          <studio-input
            type="email"
            label="Email"
            name="email"
            required
            .value=${this.email}
            @input=${(e: any) => this.email = e.target.value}
          ></studio-input>
          
          <studio-input
            type="password"
            label="Password"
            name="password"
            required
            .value=${this.password}
            @input=${(e: any) => this.password = e.target.value}
          ></studio-input>
          
          <studio-button
            variant="primary"
            type="submit"
            ?disabled=${this.loading}
          >
            ${this.loading ? 'Logging in...' : 'Login'}
          </studio-button>
        </form>
        
        <p>
          Don't have an account? 
          <a href="/register">Register</a>
        </p>
      </div>
    `;
  }

  async handleSubmit(e: Event) {
    e.preventDefault();
    this.error = '';
    this.loading = true;

    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: this.email,
          password: this.password
        })
      });

      const data = await response.json();

      if (data.success) {
        authStore.login(data.user, data.token);
        window.location.href = '/apps';
      } else {
        this.error = data.error || 'Login failed';
      }
    } catch (error) {
      this.error = 'Network error. Please try again.';
    } finally {
      this.loading = false;
    }
  }
}
```

### **2. AuthStore.ts**
```typescript
import { makeAutoObservable } from 'mobx';

export interface User {
  id: number;
  email: string;
  name: string;
  roles: string[];
  status: string;
}

class AuthStore {
  user: User | null = null;
  token: string | null = null;
  initialized = false;

  constructor() {
    makeAutoObservable(this);
    this.loadFromStorage();
  }

  loadFromStorage() {
    const token = localStorage.getItem('authToken');
    const userJson = localStorage.getItem('authUser');

    if (token && userJson) {
      this.token = token;
      this.user = JSON.parse(userJson);
    }

    this.initialized = true;
  }

  login(user: User, token: string) {
    this.user = user;
    this.token = token;
    localStorage.setItem('authToken', token);
    localStorage.setItem('authUser', JSON.stringify(user));
  }

  logout() {
    this.user = null;
    this.token = null;
    localStorage.removeItem('authToken');
    localStorage.removeItem('authUser');
    window.location.href = '/login';
  }

  hasRole(role: string): boolean {
    return this.user?.roles.includes(role) || false;
  }

  hasPermission(resource: string, action: string): boolean {
    // Admin has all permissions
    if (this.hasRole('admin')) return true;

    // TODO: Check specific permissions from backend
    // For now, managers can do most things, users can read
    if (action === 'read') return true;
    if (this.hasRole('manager') && ['create', 'update'].includes(action)) return true;

    return false;
  }

  isAuthenticated(): boolean {
    return !!this.token && !!this.user;
  }

  getAuthHeader(): HeadersInit {
    return this.token ? { 'Authorization': `Bearer ${this.token}` } : {};
  }
}

export const authStore = new AuthStore();
```

---

## 🔧 Backend Implementation

### **1. PasswordService.java**
```java
package com.appbana.auth;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordService {
    
    private static final int BCRYPT_LOG_ROUNDS = 12;
    
    /**
     * Hash a plain text password using BCrypt
     */
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }
    
    /**
     * Verify a plain text password against a hash
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate password strength
     * - At least 8 characters
     * - Contains uppercase letter
     * - Contains lowercase letter
     * - Contains digit
     */
    public static boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        
        return hasUpper && hasLower && hasDigit;
    }
}
```

### **2. JwtService.java**
```java
package com.appbana.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.util.Date;
import java.util.List;

public class JwtService {
    
    // TODO: Load from environment variable in production
    private static final String SECRET_KEY = "your-256-bit-secret-key-change-in-production";
    private static final long EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
    
    private static final Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
    private static final JWTVerifier verifier = JWT.require(algorithm).build();
    
    /**
     * Generate JWT token for user
     */
    public static String generateToken(Long userId, String email, String name, List<String> roles) {
        return JWT.create()
                .withSubject(userId.toString())
                .withClaim("email", email)
                .withClaim("name", name)
                .withClaim("roles", roles)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .sign(algorithm);
    }
    
    /**
     * Verify and decode JWT token
     */
    public static DecodedJWT verifyToken(String token) throws JWTVerificationException {
        return verifier.verify(token);
    }
    
    /**
     * Extract user ID from token
     */
    public static Long getUserIdFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return Long.parseLong(jwt.getSubject());
    }
    
    /**
     * Extract roles from token
     */
    public static List<String> getRolesFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt.getClaim("roles").asList(String.class);
    }
}
```

### **3. AuthenticationService.java**
```java
package com.appbana.auth;

import com.appbana.model.User;
import com.appbana.repository.UserRepository;

import java.util.Optional;

public class AuthenticationService {
    
    private final UserRepository userRepository;
    
    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Register new user
     */
    public RegisterResult register(String email, String password, String name) {
        // Validate email format
        if (!isValidEmail(email)) {
            return RegisterResult.error("Invalid email format");
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            return RegisterResult.error("Email already registered");
        }
        
        // Validate password strength
        if (!PasswordService.isPasswordStrong(password)) {
            return RegisterResult.error("Password must be at least 8 characters with uppercase, lowercase, and digit");
        }
        
        // Hash password
        String passwordHash = PasswordService.hashPassword(password);
        
        // Create user
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setName(name);
        user.setStatus("active");
        
        user = userRepository.save(user);
        
        // Assign default 'user' role
        userRepository.assignRole(user.getId(), "user");
        
        // Load roles
        List<String> roles = userRepository.getUserRoles(user.getId());
        
        // Generate token
        String token = JwtService.generateToken(user.getId(), user.getEmail(), user.getName(), roles);
        
        return RegisterResult.success(user, token, roles);
    }
    
    /**
     * Login user
     */
    public LoginResult login(String email, String password) {
        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return LoginResult.error("Invalid credentials");
        }
        
        User user = userOpt.get();
        
        // Verify password
        if (!PasswordService.verifyPassword(password, user.getPasswordHash())) {
            return LoginResult.error("Invalid credentials");
        }
        
        // Check account status
        if (!"active".equals(user.getStatus())) {
            return LoginResult.error("Account is " + user.getStatus());
        }
        
        // Update last login
        userRepository.updateLastLogin(user.getId());
        
        // Load roles
        List<String> roles = userRepository.getUserRoles(user.getId());
        
        // Generate token
        String token = JwtService.generateToken(user.getId(), user.getEmail(), user.getName(), roles);
        
        return LoginResult.success(user, token, roles);
    }
    
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
```

---

## 📝 Implementation Checklist

### Phase 1: Foundation (Week 1)
- [ ] Add BCrypt dependency to pom.xml
- [ ] Add JWT library (auth0 java-jwt) to pom.xml
- [ ] Create database migration scripts (users, roles, permissions tables)
- [ ] Create User, Role, Permission entity models
- [ ] Create UserRepository, RoleRepository with JDBC
- [ ] Implement PasswordService.java
- [ ] Implement JwtService.java
- [ ] Write unit tests for password hashing and JWT

### Phase 2: Backend API (Week 1-2)
- [ ] Implement AuthenticationService.java
- [ ] Add POST /api/auth/register endpoint
- [ ] Add POST /api/auth/login endpoint
- [ ] Add POST /api/auth/logout endpoint
- [ ] Add GET /api/auth/me endpoint
- [ ] Create AuthenticationFilter (JWT middleware)
- [ ] Add authorization checks to existing API routes
- [ ] Test authentication flow with Postman/curl

### Phase 3: Frontend UI (Week 2)
- [ ] Create AuthStore.ts (MobX store)
- [ ] Create LoginForm.ts component
- [ ] Create RegisterForm.ts component
- [ ] Create ProtectedRoute.ts wrapper
- [ ] Add login/register pages to router
- [ ] Update API client to include JWT token
- [ ] Test login/register flow in browser

### Phase 4: RBAC Implementation (Week 3)
- [ ] Create RoleService.java
- [ ] Create PermissionService.java
- [ ] Add role management endpoints (CRUD roles)
- [ ] Add permission management endpoints
- [ ] Create RoleManager UI component in Studio
- [ ] Implement permission checking in DataTableElement
- [ ] Hide/show buttons based on permissions
- [ ] Test permission-based access

### Phase 5: AI Integration (Week 4)
- [ ] Add User/Role entities to builder-database
- [ ] Update AiSystemPrompts.java to include auth patterns
- [ ] Test AI generation: "Add user authentication"
- [ ] Update form-patterns.json with login/register forms
- [ ] Test AI: "Create a registration form"

### Phase 6: Testing & Documentation (Week 4)
- [ ] Write integration tests for auth flow
- [ ] Write tests for RBAC
- [ ] Security audit (password storage, token expiration)
- [ ] Update user documentation
- [ ] Create admin guide for role management
- [ ] Add security best practices doc

---

**Estimated Total Time**: 4-6 weeks (1 developer full-time)

**Next Step**: Start with database schema and PasswordService implementation

---

# Appendix: Phase 1 Implementation Plan

# Authentication Phase 1 - Enterprise Critical Features

## Executive Summary
**Status**: Starting Phase 1 Implementation (6 weeks, $80K-120K)  
**Current Grade**: 6/10 (MVP-Ready, Enterprise-Incomplete)  
**Target Grade**: 8.5/10 (Enterprise-Ready)  
**Started**: November 22, 2025

## Phase 1 Critical Fixes (URGENT)

### 1. Multi-Tenancy - SHOWSTOPPER for SaaS ⚠️
**Problem**: Single-tenant architecture cannot host multiple customers  
**Impact**: Blocks SaaS business model entirely  
**Timeline**: Week 5-6 (requires foundation from weeks 1-4)  
**Effort**: HIGH (refactor ALL tables + queries)

**Implementation**:
```sql
-- New organization table
CREATE TABLE organization (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    max_users INT DEFAULT 100,
    max_storage_gb INT DEFAULT 10,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add to ALL existing tables
ALTER TABLE app_user ADD COLUMN organization_id VARCHAR(36);
ALTER TABLE role ADD COLUMN organization_id VARCHAR(36);
ALTER TABLE permission ADD COLUMN organization_id VARCHAR(36);
-- ... repeat for all tables
```

### 2. Field-Level Security - BLOCKER for Healthcare/Finance 🏥
**Problem**: No granular field permissions (can't hide User.salary from non-HR)  
**Impact**: Cannot pass HIPAA/PCI-DSS audits  
**Timeline**: Week 1-2 (STARTING NOW)  
**Effort**: MEDIUM (new table + runtime filtering)

**Implementation**:
```sql
CREATE TABLE field_permission (
    id VARCHAR(36) PRIMARY KEY,
    role_id VARCHAR(36) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    readable BOOLEAN DEFAULT TRUE,
    editable BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    UNIQUE(role_id, entity_name, field_name)
);
```

### 3. Role Hierarchy - REQUIRED for 90% of Enterprises 📊
**Problem**: Flat roles (no manager → subordinate visibility)  
**Impact**: Cannot model org charts, managers can't see team data  
**Timeline**: Week 3-4  
**Effort**: MEDIUM (recursive queries + materialized path)

**Implementation**:
```sql
-- Add parent-child relationship
ALTER TABLE role ADD COLUMN parent_role_id VARCHAR(36);
ALTER TABLE role ADD FOREIGN KEY (parent_role_id) REFERENCES role(id) ON DELETE SET NULL;

-- Materialized path for fast queries
CREATE TABLE role_hierarchy (
    role_id VARCHAR(36) NOT NULL,
    ancestor_role_id VARCHAR(36) NOT NULL,
    distance INT NOT NULL,
    PRIMARY KEY (role_id, ancestor_role_id),
    FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE,
    FOREIGN KEY (ancestor_role_id) REFERENCES role(id) ON DELETE CASCADE
);
```

### 4. Profile Layer - ESSENTIAL for Usability 👤
**Problem**: Assigning permissions one-by-one is 10x admin overhead  
**Impact**: Poor UX, admin fatigue, deployment delays  
**Timeline**: Week 2-3  
**Effort**: LOW (templates + inheritance logic)

**Implementation**:
```sql
CREATE TABLE profile (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE profile_permission (
    profile_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (profile_id, permission_id),
    FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission(id) ON DELETE CASCADE
);

ALTER TABLE app_user ADD COLUMN profile_id VARCHAR(36);
ALTER TABLE app_user ADD FOREIGN KEY (profile_id) REFERENCES profile(id) ON DELETE SET NULL;
```

### 5. Session Management - SECURITY BEST PRACTICE 🔒
**Problem**: JWT tokens cannot be revoked (if compromised, valid until expiration)  
**Impact**: Security risk, cannot force logout, no device tracking  
**Timeline**: Week 4-5  
**Effort**: MEDIUM (session tracking + cleanup jobs)

**Implementation**:
```sql
CREATE TABLE user_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_user ON user_session(user_id);
CREATE INDEX idx_session_token ON user_session(token_hash);
CREATE INDEX idx_session_expires ON user_session(expires_at);
```

---

## Week-by-Week Implementation Plan

### Week 1-2: Field-Level Security (FLS)
**Goal**: Granular field permissions for compliance

**Deliverables**:
1. **Database Schema**:
   - Create `field_permission` table
   - Add indexes on `role_id`, `entity_name`
   - Seed default FLS: HR sees salary, managers see performance

2. **Backend Code**:
   ```java
   // com.appbana.model.FieldPermission.java
   public class FieldPermission {
       private String id;
       private String roleId;
       private String entityName;
       private String fieldName;
       private boolean readable;
       private boolean editable;
   }
   
   // com.appbana.service.PermissionService.java (new methods)
   public boolean canReadField(String userId, String entityName, String fieldName);
   public boolean canEditField(String userId, String entityName, String fieldName);
   public List<String> getReadableFields(String userId, String entityName);
   public List<String> getEditableFields(String userId, String entityName);
   ```

3. **REST API Updates**:
   - Update `/api/{entity}` GET to filter fields based on FLS
   - Update `/api/{entity}` PUT to validate field editability
   - Add `/api/field-permissions` CRUD endpoints

4. **UI Components**:
   - Update `FormElement.ts` to hide non-readable fields
   - Disable editing for non-editable fields
   - Show tooltip: "Field hidden by admin" for masked fields

5. **Testing**:
   - Manager can see `User.name` but NOT `User.salary`
   - HR can see `User.salary` but NOT `User.performance_review`
   - Admin can see ALL fields

**Success Criteria**: FLS prevents unauthorized field access with 99.9% accuracy

---

### Week 2-3: Profile Layer
**Goal**: Reduce permission assignment from 2 hours → 10 minutes

**Deliverables**:
1. **Database Schema**:
   - Create `profile` and `profile_permission` tables
   - Add `profile_id` to `app_user`
   - Seed default profiles: System Administrator, Standard User, Read Only

2. **Backend Code**:
   ```java
   // com.appbana.model.Profile.java
   public class Profile {
       private String id;
       private String name;
       private String description;
       private List<Permission> permissions;
   }
   
   // Update Role.hasPermission() to check profile + role
   public boolean hasPermission(Permission required) {
       // Check profile permissions
       if (user.getProfile().hasPermission(required)) return true;
       // Check direct role permissions
       return permissions.stream().anyMatch(p -> p.matches(required));
   }
   ```

3. **REST API**:
   - Add `/api/profiles` CRUD endpoints
   - Add `/api/profiles/{id}/permissions` management
   - Update `/api/users` to support profile assignment

4. **UI Components**:
   - Create `ProfileManager.ts` in Studio
   - Profile dropdown in user creation form
   - Profile editor with permission checklist

5. **Testing**:
   - Assign "Standard User" profile → user gets 20 permissions instantly
   - Change profile → permissions update immediately
   - Profile + Role permissions combine correctly

**Success Criteria**: New user setup takes <10 minutes (vs 2 hours manual)

---

### Week 3-4: Role Hierarchy
**Goal**: Managers see subordinates' records automatically

**Deliverables**:
1. **Database Schema**:
   - Add `parent_role_id` to `role` table
   - Create `role_hierarchy` table (materialized path)
   - Add stored procedure: `rebuild_role_hierarchy()`

2. **Backend Code**:
   ```java
   // com.appbana.service.RoleHierarchyService.java
   public List<Role> getSubordinateRoles(String roleId);
   public List<User> getSubordinateUsers(String userId);
   public void rebuildHierarchy();
   
   // Update visibility queries
   // Before: WHERE created_by = ?
   // After:  WHERE created_by IN (SELECT user_id FROM subordinate_users(?))
   ```

3. **REST API**:
   - Update ALL entity queries to include hierarchical visibility
   - Add `/api/roles/{id}/hierarchy` to view org chart
   - Add `/api/users/subordinates` endpoint

4. **UI Components**:
   - Create `RoleHierarchyTree.ts` in Studio
   - Drag-and-drop role parent assignment
   - Visual org chart with user counts

5. **Testing**:
   - 5-level hierarchy: CEO → VP → Manager → Team Lead → Employee
   - Manager query for subordinate records completes <100ms
   - Changing parent role updates visibility within 1 second

**Success Criteria**: Managers have automatic visibility into team records

---

### Week 4-5: Session Management
**Goal**: Token revocation + device tracking

**Deliverables**:
1. **Database Schema**:
   - Create `user_session` table
   - Add cleanup job: `DELETE FROM user_session WHERE expires_at < NOW() OR revoked_at IS NOT NULL`

2. **Backend Code**:
   ```java
   // com.appbana.service.SessionService.java
   public String createSession(String userId, String token, String ipAddress, String userAgent);
   public boolean isSessionValid(String tokenHash);
   public void revokeSession(String sessionId);
   public void revokeAllUserSessions(String userId);
   public List<UserSession> getActiveSessions(String userId);
   ```

3. **Update AuthenticationFilter**:
   ```java
   // Before: Only verify JWT signature + expiration
   // After:  ALSO check if session exists and not revoked
   String tokenHash = hashToken(token);
   if (!sessionService.isSessionValid(tokenHash)) {
       return unauthorized("Session revoked");
   }
   ```

4. **REST API**:
   - POST `/api/auth/logout` - revoke current session
   - POST `/api/auth/logout-all` - revoke all user sessions
   - GET `/api/auth/sessions` - list active sessions

5. **UI Components**:
   - Create `ActiveSessions.ts` page
   - Show: Device, IP, Location, Last Activity
   - "Logout" button per session

6. **Testing**:
   - Login → create session → logout → token becomes invalid within 1 second
   - Password change → all sessions revoked automatically
   - Show 3 active devices: Chrome (Windows), Safari (iPhone), Firefox (Mac)

**Success Criteria**: Token revocation takes effect within 1 second

---

### Week 5-6: Multi-Tenancy (BIG REFACTOR)
**Goal**: Complete tenant isolation for SaaS

**Deliverables**:
1. **Database Schema** (MAJOR):
   ```sql
   -- Step 1: Create organization
   CREATE TABLE organization (
       id VARCHAR(36) PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       subdomain VARCHAR(100) UNIQUE NOT NULL,
       status VARCHAR(20) DEFAULT 'ACTIVE',
       max_users INT DEFAULT 100,
       max_storage_gb INT DEFAULT 10,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );
   
   -- Step 2: Add organization_id to ALL tables (23 tables)
   ALTER TABLE app_user ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE role ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE permission ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE profile ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE field_permission ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE user_session ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE user_role ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE role_permission ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE profile_permission ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE role_hierarchy ADD COLUMN organization_id VARCHAR(36);
   ALTER TABLE audit_log ADD COLUMN organization_id VARCHAR(36);
   -- Add to ALL AppBana tables: app_meta, page_meta, entity_meta, etc.
   
   -- Step 3: Add foreign keys
   ALTER TABLE app_user ADD FOREIGN KEY (organization_id) REFERENCES organization(id);
   -- Repeat for all tables
   
   -- Step 4: Update unique constraints to be per-org
   ALTER TABLE app_user DROP CONSTRAINT IF EXISTS unique_email;
   ALTER TABLE app_user ADD CONSTRAINT unique_email_per_org UNIQUE(organization_id, email);
   ```

2. **Migration Script**:
   ```sql
   -- Create default organization
   INSERT INTO organization (id, name, subdomain) 
   VALUES ('default-org-id', 'Default Organization', 'default');
   
   -- Assign all existing data to default org
   UPDATE app_user SET organization_id = 'default-org-id';
   UPDATE role SET organization_id = 'default-org-id';
   -- Repeat for all tables
   
   -- Make organization_id NOT NULL after migration
   ALTER TABLE app_user ALTER COLUMN organization_id SET NOT NULL;
   ```

3. **Backend Code**:
   ```java
   // com.appbana.context.OrganizationContext.java (ThreadLocal)
   public class OrganizationContext {
       private static ThreadLocal<String> currentOrgId = new ThreadLocal<>();
       
       public static String getCurrentOrganizationId() {
           return currentOrgId.get();
       }
       
       public static void setCurrentOrganizationId(String orgId) {
           currentOrgId.set(orgId);
       }
   }
   
   // Update AuthenticationFilter to set org context
   String subdomain = extractSubdomain(request);
   Organization org = organizationService.findBySubdomain(subdomain);
   OrganizationContext.setCurrentOrganizationId(org.getId());
   
   // Update ALL queries to filter by org
   // Before: SELECT * FROM app_user WHERE id = ?
   // After:  SELECT * FROM app_user WHERE id = ? AND organization_id = ?
   ```

4. **REST API**:
   - Add `/api/organizations` CRUD (admin only)
   - Update ALL endpoints to auto-filter by organization_id
   - Add `/api/organizations/{id}/users` for user management

5. **UI Components**:
   - Subdomain-based routing: `acme.appbana.com`, `widgets.appbana.com`
   - Organization switcher for multi-org users
   - Organization settings page

6. **Testing** (CRITICAL):
   - Create 2 orgs: "Acme Corp" (subdomain: acme), "Widget Inc" (subdomain: widgets)
   - Create identical users: john@example.com in both orgs
   - Run 10,000 test queries → ZERO cross-org data leakage
   - Performance: Queries with organization_id filter <100ms

**Success Criteria**: Complete tenant isolation, SaaS-ready architecture

---

## Technical Metrics (Phase 1 Completion)

### Performance
- [ ] Field-Level Security: Query overhead <10ms per request
- [ ] Role Hierarchy: Subordinate queries <100ms for 5-level tree
- [ ] Session Management: Token validation <5ms
- [ ] Multi-Tenancy: Organization filter <100ms (with proper indexes)

### Security
- [ ] Field-Level Security: 99.9% accuracy in field masking
- [ ] Session Management: Token revocation within 1 second
- [ ] Multi-Tenancy: ZERO cross-org leakage in 10,000 test queries

### Usability
- [ ] Profile Layer: User setup <10 minutes (vs 2 hours)
- [ ] Role Hierarchy: Visual org chart in Studio
- [ ] Session Management: Active sessions page with device info

### Compliance
- [ ] HIPAA-ready: FLS protects PHI fields
- [ ] SOC 2-ready: Audit logging + session tracking
- [ ] Multi-tenant: Passes ISO 27001 data isolation requirements

---

## Business Impact (Phase 1)

### Revenue Unlock
- **Mid-Market Sales**: $500K-2M ARR (1000-5000 users per customer)
- **Regulated Industries**: Healthcare, Finance (FLS required)
- **SaaS Model**: Multi-tenancy enables $10M+ ARR potential

### Competitive Position
- **Before Phase 1**: "Nice metadata platform, but not for us" (enterprise rejects)
- **After Phase 1**: "Salesforce alternative with better UX" (enterprise evaluates)

### Customer Expansion
- **Small Teams** (<50 users): Already supported with current MVP
- **Mid-Market** (50-1000 users): Unlocked by Profile + Role Hierarchy
- **Enterprise** (1000+ users): Unlocked by Multi-Tenancy + FLS

---

## Risk Management

### Technical Risks
1. **Multi-Tenancy Migration**: 
   - Risk: Data loss during organization_id migration
   - Mitigation: Full database backup, staged rollout, rollback plan
   
2. **Performance Degradation**:
   - Risk: organization_id filter slows queries
   - Mitigation: Composite indexes on (organization_id, id), query optimization

3. **Breaking Changes**:
   - Risk: Existing apps break after multi-tenancy refactor
   - Mitigation: Backward compatibility layer, deprecation warnings

### Business Risks
1. **Timeline Slippage**:
   - Risk: 6 weeks → 10 weeks (66% overrun common in auth projects)
   - Mitigation: Weekly checkpoints, cut scope if needed (defer FLS to Week 7)

2. **ROI Uncertainty**:
   - Risk: $80K-120K investment doesn't yield enterprise sales
   - Mitigation: Pilot with 2-3 enterprise prospects before full Phase 1

---

## Go/No-Go Decision Framework

### GO Criteria (Proceed with Phase 1)
✅ Target market: Mid-market or enterprise (1000+ users)  
✅ Compliance needs: HIPAA, PCI-DSS, ISO 27001  
✅ Business model: SaaS with multiple customers  
✅ Budget: $80K-120K available for 6 weeks  
✅ Timeline: Can wait 6 weeks for enterprise readiness  

### NO-GO Criteria (Stay with MVP)
❌ Target market: Small teams (<50 users)  
❌ Compliance: Not required  
❌ Business model: On-premise, single-tenant  
❌ Budget: <$50K  
❌ Timeline: Need to ship next week  

---

## Success Definition

**Phase 1 Complete When**:
1. All 5 features implemented and tested
2. Technical metrics met (99.9% FLS accuracy, <100ms queries, zero leakage)
3. Passes Healthcare HIPAA security audit
4. Supports 1,000 users per organization
5. Documentation: Admin guides for all 5 features
6. Demo: Multi-tenant deployment with 3 organizations

**Business Outcome**: Unlocks mid-market enterprise sales ($500K-2M ARR)

---

## Next Steps

**Immediate Actions** (Starting Week 1):
1. ✅ Create this implementation document
2. ⏳ Create Field-Level Security schema migration
3. ⏳ Implement FieldPermission entity and repository
4. ⏳ Create PermissionService.canReadField() method
5. ⏳ Update REST endpoints to filter fields

**Weekly Cadence**:
- Monday: Sprint planning, design review
- Wednesday: Mid-week checkpoint
- Friday: Demo + retrospective

**Stakeholder Updates**:
- Weekly: Technical progress report
- Bi-weekly: Business impact review
- End of Phase 1: Executive readiness assessment

---

**Document Version**: 1.0  
**Last Updated**: November 22, 2025  
**Owner**: Development Team  
**Approved By**: [Pending]
