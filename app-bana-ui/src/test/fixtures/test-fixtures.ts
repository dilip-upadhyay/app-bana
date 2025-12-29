// Frontend test fixtures
// Copy-paste ready test data for all frontend tests
// See ENTITY_FORM_BINDING_TEST_PLAN.md for complete test plan

export const VALID_USER = {
  firstName: "John",
  lastName: "Doe",
  email: "john@example.com",
  phone: "+1234567890",
  password: "SecurePass123",
  confirmPassword: "SecurePass123"
};

export const INVALID_EMAIL_USER = {
  ...VALID_USER,
  email: "invalid-email"  // Missing @ symbol
};

export const WEAK_PASSWORD_USER = {
  ...VALID_USER,
  password: "123"  // Too short
};

export const MISMATCHED_PASSWORD_USER = {
  ...VALID_USER,
  password: "SecurePass123",
  confirmPassword: "DifferentPass456"
};

export const DUPLICATE_EMAIL_USER = {
  ...VALID_USER,
  email: "existing@example.com"  // Already in DB
};

export const SQL_INJECTION_USER = {
  ...VALID_USER,
  firstName: "Robert'; DROP TABLE user;--"
};

export const XSS_USER = {
  ...VALID_USER,
  email: "<script>alert('XSS')</script>@example.com"
};

// Mock API responses
export const MOCK_BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

export const MOCK_CSRF_TOKEN = "a1b2c3d4e5f6g7h8i9j0";

export const MOCK_API_SUCCESS_RESPONSE = {
  ok: true,
  data: {
    id: 123,
    firstName: "John",
    lastName: "Doe",
    email: "john@example.com",
    createdAt: "2025-12-30T10:00:00Z"
  }
};

export const MOCK_API_VALIDATION_ERROR = {
  ok: false,
  validationErrors: {
    email: "Email already exists",
    password: "Password must be at least 8 characters"
  }
};

export const MOCK_API_RATE_LIMIT_ERROR = {
  ok: false,
  error: "Too many signup attempts. Try again in 1 minute.",
  retryAfter: 60
};

export const MOCK_API_SERVER_ERROR = {
  ok: false,
  error: "Internal server error"
};
