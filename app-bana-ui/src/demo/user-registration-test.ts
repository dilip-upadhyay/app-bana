/**
 * User Registration Test App
 * Demonstrates the complete flow:
 * 1. Define User entity with EntityMeta
 * 2. Connect to LocalStorage datasource
 * 3. Create registration form
 * 4. Save and retrieve user data
 */

import { EntityMeta } from '../models/entity-metadata';
import { JsonFileAdapter } from '../core/adapters/JsonFileAdapter';
import { AdapterRegistry } from '../core/AdapterRegistry';
import { 
  syncEntityToBackend, 
  previewBackendSchema, 
  listBackendSchemas,
  getBackendSchema 
} from '../core/backend-sync';

// 1. Define User Entity
export const UserEntity: EntityMeta = {
  id: 'user-entity-001',
  name: 'User',
  displayName: 'User',
  pluralName: 'Users',
  description: 'User registration entity for testing',
  tableName: 'users',
  datasource: 'user-storage',
  datasourceType: 'localstorage',
  datasourceConfig: {
    file: {
      filePath: 'app-bana-users',
      format: 'json'
    }
  },
  fields: [
    {
      id: 'field-id',
      name: 'id',
      type: 'autoincrement',
      required: true,
      unique: true,
      display: {
        label: 'ID',
        hidden: true
      }
    },
    {
      id: 'field-email',
      name: 'email',
      type: 'email',
      required: true,
      unique: true,
      validation: {
        pattern: String.raw`^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`,
        customMessage: 'Please enter a valid email address'
      },
      display: {
        label: 'Email'
      }
    },
    {
      id: 'field-firstname',
      name: 'firstName',
      type: 'text',
      required: true,
      unique: false,
      validation: {
        minLength: 2,
        maxLength: 50,
        customMessage: 'First name must be between 2 and 50 characters'
      },
      display: {
        label: 'First Name'
      }
    },
    {
      id: 'field-lastname',
      name: 'lastName',
      type: 'text',
      required: true,
      unique: false,
      validation: {
        minLength: 2,
        maxLength: 50,
        customMessage: 'Last name must be between 2 and 50 characters'
      },
      display: {
        label: 'Last Name'
      }
    },
    {
      id: 'field-password',
      name: 'password',
      type: 'text',
      required: true,
      unique: false,
      validation: {
        minLength: 8,
        customMessage: 'Password must be at least 8 characters'
      },
      display: {
        label: 'Password',
        helpText: 'Minimum 8 characters'
      }
    },
    {
      id: 'field-dob',
      name: 'dateOfBirth',
      type: 'date',
      required: false,
      unique: false,
      display: {
        label: 'Date of Birth'
      }
    },
    {
      id: 'field-phone',
      name: 'phoneNumber',
      type: 'phone',
      required: false,
      unique: false,
      validation: {
        pattern: String.raw`^\+?[1-9]\d{1,14}$`,
        customMessage: 'Please enter a valid phone number'
      },
      display: {
        label: 'Phone Number'
      }
    },
    {
      id: 'field-created',
      name: 'createdAt',
      type: 'datetime',
      required: false,
      unique: false,
      display: {
        label: 'Created At',
        readOnly: true
      }
    }
  ],
  permissions: {
    auditLog: false
  }
};

// 2. Initialize Datasource
let userAdapter: JsonFileAdapter | null = null;

export async function initializeUserDatasource(): Promise<JsonFileAdapter> {
  if (userAdapter) {
    return userAdapter;
  }

  const registry = AdapterRegistry.getInstance();
  
  // Create LocalStorage adapter
  userAdapter = registry.create('localstorage', {
    storageKey: 'app-bana-users',
    storageType: 'localstorage'
  }) as JsonFileAdapter;

  // Connect to storage
  await userAdapter.connect({
    storageKey: 'app-bana-users',
    storageType: 'localstorage'
  });

  console.log('✅ User datasource initialized (LocalStorage)');
  return userAdapter;
}

// 3. User Registration Logic
export interface UserRegistrationData {
  email: string;
  firstName: string;
  lastName: string;
  password: string;
  dateOfBirth?: string;
  phoneNumber?: string;
}

export async function registerUser(userData: UserRegistrationData): Promise<any> {
  const adapter = await initializeUserDatasource();
  
  // Check if email already exists
  const existingUsers = await adapter.query('users', {
    filters: [{ field: 'email', operator: 'eq', value: userData.email }]
  });

  if (existingUsers.data.length > 0) {
    throw new Error('Email already registered');
  }

  // Create user with timestamp
  const newUser = {
    ...userData,
    createdAt: new Date().toISOString()
  };

  const savedUser = await adapter.create('users', newUser);
  console.log('✅ User registered:', savedUser);
  return savedUser;
}

export async function getAllUsers(): Promise<any[]> {
  const adapter = await initializeUserDatasource();
  const result = await adapter.query('users', {
    sort: [{ field: 'createdAt', desc: true }]
  });
  return result.data;
}

export async function getUserByEmail(email: string): Promise<Record<string, any> | null> {
  const adapter = await initializeUserDatasource();
  const result = await adapter.query('users', {
    filters: [{ field: 'email', operator: 'eq', value: email }]
  });
  return result.data.length > 0 ? result.data[0] : null;
}

export async function deleteUser(id: string): Promise<void> {
  const adapter = await initializeUserDatasource();
  await adapter.delete('users', id);
  console.log('✅ User deleted:', id);
}

export async function clearAllUsers(): Promise<void> {
  const adapter = await initializeUserDatasource();
  await adapter.clearAll();
  console.log('✅ All users cleared from LocalStorage');
}

// 4. Demo Functions (accessible from browser console)
export const userRegistrationDemo = {
  // Show entity definition
  showEntity: () => {
    console.log('📋 User Entity Definition:', UserEntity);
    return UserEntity;
  },

  // Register a test user
  registerTestUser: async () => {
    try {
      const testUser = {
        email: 'john.doe@example.com',
        firstName: 'John',
        lastName: 'Doe',
        password: 'SecurePass123!',
        dateOfBirth: '1990-05-15',
        phoneNumber: '+1234567890'
      };
      const user = await registerUser(testUser);
      console.log('✅ Test user registered:', user);
      return user;
    } catch (error) {
      console.error('❌ Registration failed:', error);
      throw error;
    }
  },

  // Register multiple users
  registerMultipleUsers: async () => {
    const users = [
      { email: 'alice@test.com', firstName: 'Alice', lastName: 'Smith', password: 'password123' },
      { email: 'bob@test.com', firstName: 'Bob', lastName: 'Johnson', password: 'password456' },
      { email: 'carol@test.com', firstName: 'Carol', lastName: 'Williams', password: 'password789' }
    ];

    const registered = [];
    for (const userData of users) {
      try {
        const user = await registerUser(userData);
        registered.push(user);
      } catch (error) {
        console.warn(`⚠️ Skipped ${userData.email}:`, error);
      }
    }
    console.log(`✅ Registered ${registered.length} users`);
    return registered;
  },

  // List all users
  listUsers: async () => {
    const users = await getAllUsers();
    console.log(`📋 Total users: ${users.length}`);
    console.table(users.map(u => ({
      id: u.id,
      email: u.email,
      name: `${u.firstName} ${u.lastName}`,
      createdAt: u.createdAt
    })));
    return users;
  },

  // Find user by email
  findByEmail: async (email: string) => {
    const user = await getUserByEmail(email);
    if (user) {
      console.log('✅ User found:', user);
    } else {
      console.log('❌ User not found');
    }
    return user;
  },

  // Delete user
  deleteUser: async (id: string) => {
    await deleteUser(id);
  },

  // Clear all data
  clearAll: async () => {
    await clearAllUsers();
  },

  // Run complete demo
  runFullDemo: async () => {
    console.log('🚀 Starting User Registration Demo...\n');

    console.log('1️⃣ Entity Definition');
    userRegistrationDemo.showEntity();

    console.log('\n2️⃣ Registering test user...');
    await userRegistrationDemo.registerTestUser();

    console.log('\n3️⃣ Registering multiple users...');
    await userRegistrationDemo.registerMultipleUsers();

    console.log('\n4️⃣ Listing all users...');
    await userRegistrationDemo.listUsers();

    console.log('\n5️⃣ Finding user by email...');
    await userRegistrationDemo.findByEmail('alice@test.com');

    console.log('\n✅ Demo complete! Check LocalStorage in DevTools > Application > Local Storage');
  },

  // === Backend Sync Functions ===

  // Preview backend SQL (without creating table)
  previewBackendSQL: async () => {
    try {
      console.log('📋 Generating backend SQL for User entity...');
      const sqlStatements = await previewBackendSchema(UserEntity);
      console.log('\n✅ Backend SQL DDL:');
      for (const sql of sqlStatements) {
        console.log(sql);
      }
      return sqlStatements;
    } catch (error) {
      console.error('❌ Failed to preview backend SQL:', error);
      throw error;
    }
  },

  // Sync entity to backend (creates database table)
  syncToBackend: async () => {
    try {
      console.log('🔄 Syncing User entity to backend...');
      const result = await syncEntityToBackend(UserEntity);
      console.log('✅ User entity synced to backend successfully!');
      console.log('Backend response:', result);
      return result;
    } catch (error) {
      console.error('❌ Failed to sync to backend:', error);
      throw error;
    }
  },

  // List all backend schemas
  listBackendSchemas: async () => {
    try {
      const schemas = await listBackendSchemas();
      console.log(`📋 Backend schemas (${schemas.length}):`);
      console.table(schemas);
      return schemas;
    } catch (error) {
      console.error('❌ Failed to list backend schemas:', error);
      throw error;
    }
  },

  // Get specific backend schema
  getBackendSchema: async (name: string) => {
    try {
      const schema = await getBackendSchema(name);
      console.log(`✅ Backend schema: ${name}`);
      console.log(schema);
      return schema;
    } catch (error) {
      console.error(`❌ Failed to get backend schema ${name}:`, error);
      throw error;
    }
  },

  // Run full demo with backend sync
  runFullDemoWithBackend: async () => {
    console.log('🚀 Starting User Registration Demo WITH BACKEND SYNC...\n');

    console.log('1️⃣ Entity Definition');
    userRegistrationDemo.showEntity();

    console.log('\n2️⃣ Preview backend SQL...');
    await userRegistrationDemo.previewBackendSQL();

    console.log('\n3️⃣ Sync entity to backend (create table)...');
    await userRegistrationDemo.syncToBackend();

    console.log('\n4️⃣ Registering test user...');
    await userRegistrationDemo.registerTestUser();

    console.log('\n5️⃣ Registering multiple users...');
    await userRegistrationDemo.registerMultipleUsers();

    console.log('\n6️⃣ Listing all users from LocalStorage...');
    await userRegistrationDemo.listUsers();

    console.log('\n7️⃣ List backend schemas...');
    await userRegistrationDemo.listBackendSchemas();

    console.log('\n✅ Full demo complete! LocalStorage + Backend synced!');
  }
};

// Make available globally for console testing
if (globalThis.window !== undefined) {
  (globalThis.window as any).userRegistrationDemo = userRegistrationDemo;
  (globalThis.window as any).UserEntity = UserEntity;
}
