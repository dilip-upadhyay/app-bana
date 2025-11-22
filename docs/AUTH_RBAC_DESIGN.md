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
