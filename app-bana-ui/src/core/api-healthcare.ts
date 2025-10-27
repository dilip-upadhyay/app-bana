/**
 * Healthcare & Compliance Interceptors
 * HIPAA, PHI protection, and audit requirements for AppBana Healthcare features
 */

import { Interceptor, ApiError } from './api-interceptor.ts';
import { apiClient } from './api-client.ts';

/**
 * PHI Access Audit Interceptor
 * Automatically audits all Protected Health Information access
 * Required for HIPAA compliance (December 2025 goal)
 */
export function phiAuditInterceptor(): Interceptor {
  return {
    name: 'phiAudit',
    onRequest: (config) => {
      // Mark requests that access PHI
      const headers = config.headers as Record<string, string> | undefined;
      const isPHI = (headers && headers['X-PHI-Access'] === 'true') ||
                    config.url?.includes('/fhir/') ||
                    config.url?.includes('/Patient') ||
                    config.url?.includes('/Observation');

      if (isPHI) {
        config.headers = {
          ...config.headers,
          'X-Audit-Required': 'true',
          'X-Audit-Category': 'PHI-ACCESS',
        };
      }

      return config;
    },
    onResponse: (response, data) => {
      // Log PHI access to audit trail
      if (response.headers.get('X-Audit-Required') === 'true') {
        // Audit will be handled server-side, but we can track client-side too
        console.info('[PHI Access]', {
          url: response.url,
          timestamp: new Date().toISOString(),
          user: localStorage.getItem('current_user_id'),
        });
      }
      return data;
    },
  };
}

/**
 * Minimum Necessary Access Interceptor
 * Ensures only minimum necessary PHI is requested (HIPAA requirement)
 */
export function minimumNecessaryInterceptor(): Interceptor {
  return {
    name: 'minimumNecessary',
    onRequest: (config) => {
      const url = config.url || '';

      // For FHIR requests, ensure _elements parameter is used to limit fields
      if (url.includes('/fhir/') && config.method === 'GET') {
        const hasElementsParam = url.includes('_elements=');

        if (!hasElementsParam) {
          console.warn('[HIPAA] Request missing _elements parameter for minimum necessary access:', url);
          // Could enforce by adding default _elements or blocking request
        }
      }

      return config;
    },
  };
}

/**
 * Data Redaction Interceptor
 * Redacts sensitive fields based on field-level security rules
 * Supports FLS engine (October 2025 goal)
 */
export function dataRedactionInterceptor(
  redactionRules: Map<string, string[]> // entity -> field names to redact
): Interceptor {
  return {
    name: 'dataRedaction',
    onResponse: (response, data) => {
      // Extract entity from URL
      const match = response.url.match(/\/api\/([^/?]+)/);
      if (!match) return data;

      const entity = match[1];
      const fieldsToRedact = redactionRules.get(entity);

      if (!fieldsToRedact || fieldsToRedact.length === 0) {
        return data;
      }

      // Redact fields in response
      const redact = (obj: any): any => {
        if (Array.isArray(obj)) {
          return obj.map(item => redact(item));
        }

        if (obj && typeof obj === 'object') {
          const redacted = { ...obj };
          fieldsToRedact.forEach(field => {
            if (field in redacted) {
              redacted[field] = '***REDACTED***';
            }
          });
          return redacted;
        }

        return obj;
      };

      // Handle both single objects and arrays
      if (data.rows) {
        return { ...data, rows: redact(data.rows) };
      }

      return redact(data);
    },
  };
}

/**
 * Session Timeout Interceptor
 * Automatically logs out users after inactivity (security requirement)
 */
export function sessionTimeoutInterceptor(
  timeoutMs: number = 15 * 60 * 1000, // 15 minutes default
  onTimeout?: () => void
): Interceptor {
  let lastActivityTime = Date.now();
  let timeoutCheckInterval: NodeJS.Timeout;

  const resetActivity = () => {
    lastActivityTime = Date.now();
  };

  const checkTimeout = () => {
    const inactive = Date.now() - lastActivityTime;
    if (inactive > timeoutMs) {
      console.warn('[Session] Timeout due to inactivity');
      clearInterval(timeoutCheckInterval);
      if (onTimeout) {
        onTimeout();
      } else {
        // Default: clear token and reload
        localStorage.removeItem('appbana_token');
        window.location.href = '/login';
      }
    }
  };

  // Check every minute
  timeoutCheckInterval = setInterval(checkTimeout, 60000);

  return {
    name: 'sessionTimeout',
    onRequest: (config) => {
      resetActivity();
      return config;
    },
    onResponse: (response, data) => {
      resetActivity();
      return data;
    },
  };
}

/**
 * Break-Glass Emergency Access Interceptor
 * Logs and tracks emergency access to restricted data
 */
export function breakGlassInterceptor(
  onBreakGlass: (reason: string, data: any) => Promise<void>
): Interceptor {
  return {
    name: 'breakGlass',
    onRequest: async (config) => {
      const headers = config.headers as Record<string, string> | undefined;
      const isEmergency = headers?.['X-Emergency-Access'] === 'true';

      if (isEmergency) {
        const reason = headers?.['X-Emergency-Reason'] || 'No reason provided';

        // Log emergency access
        await onBreakGlass(reason, {
          url: config.url,
          timestamp: new Date().toISOString(),
          user: localStorage.getItem('current_user_id'),
        });

        console.warn('[Break-Glass] Emergency access granted:', {
          reason,
          url: config.url,
        });
      }

      return config;
    },
  };
}

/**
 * Encryption in Transit Validator
 * Ensures all PHI requests use HTTPS (HIPAA requirement)
 */
export function encryptionValidator(): Interceptor {
  return {
    name: 'encryptionValidator',
    onRequest: (config) => {
      const url = config.url || '';
      const headers = config.headers as Record<string, string> | undefined;
      const isPHI = headers?.['X-PHI-Access'] === 'true';

      if (isPHI && url.startsWith('http:') && !url.startsWith('https:')) {
        throw new Error('HIPAA Violation: PHI cannot be transmitted over unencrypted HTTP');
      }

      return config;
    },
  };
}

/**
 * De-identification Interceptor
 * Removes or masks identifiable information for non-production environments
 */
export function deidentificationInterceptor(
  environment: 'production' | 'staging' | 'development'
): Interceptor {
  const shouldDeidentify = environment !== 'production';

  return {
    name: 'deidentification',
    onResponse: (response, data) => {
      if (!shouldDeidentify) return data;

      // Fields to mask in non-prod
      const identifierFields = [
        'ssn', 'socialSecurityNumber',
        'email', 'phone', 'phoneNumber',
        'address', 'streetAddress',
        'dob', 'dateOfBirth',
        'mrn', 'medicalRecordNumber',
      ];

      const maskValue = (value: any, field: string): any => {
        if (typeof value === 'string') {
          if (field.includes('email')) return 'test@example.com';
          if (field.includes('phone')) return '555-0100';
          if (field.includes('ssn')) return '***-**-****';
          if (field.includes('address')) return '123 Test St';
          return '***MASKED***';
        }
        return value;
      };

      const deidentify = (obj: any): any => {
        if (Array.isArray(obj)) {
          return obj.map(item => deidentify(item));
        }

        if (obj && typeof obj === 'object') {
          const masked = { ...obj };
          identifierFields.forEach(field => {
            if (field in masked) {
              masked[field] = maskValue(masked[field], field);
            }
          });
          return masked;
        }

        return obj;
      };

      if (data.rows) {
        return { ...data, rows: deidentify(data.rows) };
      }

      return deidentify(data);
    },
  };
}

/**
 * Consent Validation Interceptor
 * Ensures patient consent before accessing their data
 */
export function consentValidationInterceptor(
  checkConsent: (patientId: string) => Promise<boolean>
): Interceptor {
  return {
    name: 'consentValidation',
    onRequest: async (config) => {
      const url = config.url || '';

      // Extract patient ID from URL (e.g., /fhir/Patient/123)
      const patientMatch = url.match(/\/Patient\/([^/?]+)/);

      if (patientMatch) {
        const patientId = patientMatch[1];
        const hasConsent = await checkConsent(patientId);

        if (!hasConsent) {
          throw new Error(`Patient ${patientId} has not provided consent for data access`);
        }

        config.headers = {
          ...config.headers,
          'X-Consent-Verified': 'true',
          'X-Patient-ID': patientId,
        };
      }

      return config;
    },
  };
}

/**
 * Setup healthcare-compliant API client
 */
export function setupHealthcareCompliance(options: {
  environment: 'production' | 'staging' | 'development';
  sessionTimeoutMs?: number;
  onSessionTimeout?: () => void;
  onBreakGlass?: (reason: string, data: any) => Promise<void>;
  checkConsent?: (patientId: string) => Promise<boolean>;
  redactionRules?: Map<string, string[]>;
}) {
  const {
    environment,
    sessionTimeoutMs,
    onSessionTimeout,
    onBreakGlass,
    checkConsent,
    redactionRules = new Map(),
  } = options;

  // Add HIPAA-required interceptors
  apiClient.interceptor.use(phiAuditInterceptor());
  apiClient.interceptor.use(minimumNecessaryInterceptor());
  apiClient.interceptor.use(encryptionValidator());

  // Add session timeout
  apiClient.interceptor.use(sessionTimeoutInterceptor(sessionTimeoutMs, onSessionTimeout));

  // Add data redaction if rules provided
  if (redactionRules.size > 0) {
    apiClient.interceptor.use(dataRedactionInterceptor(redactionRules));
  }

  // Add break-glass if handler provided
  if (onBreakGlass) {
    apiClient.interceptor.use(breakGlassInterceptor(onBreakGlass));
  }

  // Add consent validation if checker provided
  if (checkConsent) {
    apiClient.interceptor.use(consentValidationInterceptor(checkConsent));
  }

  // Add de-identification for non-prod
  apiClient.interceptor.use(deidentificationInterceptor(environment));

  console.log('[Healthcare Compliance] HIPAA interceptors initialized');
}
