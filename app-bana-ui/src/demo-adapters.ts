/**
 * Adapter Demo
 * Demonstrates how to use the Universal Datasource Adapter System
 */

import { RestApiAdapter, JsonFileAdapter, adapterRegistry } from './core';

/**
 * Demo 1: REST API Adapter - GitHub API
 */
export async function demoRestApi() {
  console.log('=== REST API Adapter Demo ===');
  
  const adapter = new RestApiAdapter();
  
  await adapter.connect({
    baseUrl: 'https://api.github.com',
    authType: 'none',
    paginationStyle: 'page',
    rateLimit: 60
  });
  
  // Test connection
  const testResult = await adapter.testConnection();
  console.log('Connection test:', testResult);
  
  // Query repositories
  const result = await adapter.query('search/repositories', {
    filters: [
      { field: 'q', operator: 'eq', value: 'appbana' }
    ],
    limit: 5
  });
  
  console.log(`Found ${result.total} repositories`);
  console.log('First repo:', result.data[0]);
  
  await adapter.disconnect();
}

/**
 * Demo 2: JSON File Adapter - In-Memory Storage
 */
export async function demoJsonFile() {
  console.log('\n=== JSON File Adapter Demo ===');
  
  const adapter = new JsonFileAdapter();
  
  await adapter.connect({
    storageType: 'memory',
    initialData: {
      customers: [
        { id: '1', name: 'Acme Corp', email: 'info@acme.com', balance: 5000 },
        { id: '2', name: 'TechStart Inc', email: 'hello@techstart.io', balance: 2500 }
      ]
    }
  });
  
  // Query all customers
  let result = await adapter.query('customers', {
    sort: [{ field: 'balance', desc: true }]
  });
  console.log('All customers:', result.data);
  
  // Filter by name
  result = await adapter.query('customers', {
    filters: [{ field: 'name', operator: 'contains', value: 'Acme' }]
  });
  console.log('Filtered customers:', result.data);
  
  // Create new customer
  const newCustomer = await adapter.create('customers', {
    name: 'New Company LLC',
    email: 'contact@newco.com',
    balance: 1000
  });
  console.log('Created customer:', newCustomer);
  
  // Update customer
  const updated = await adapter.update('customers', newCustomer.id, {
    balance: 1500
  });
  console.log('Updated customer:', updated);
  
  // Get by ID
  const found = await adapter.get('customers', newCustomer.id);
  console.log('Found customer:', found);
  
  // Delete customer
  await adapter.delete('customers', newCustomer.id);
  console.log('Deleted customer:', newCustomer.id);
  
  // Query after delete
  result = await adapter.query('customers', {});
  console.log(`Customers after delete: ${result.total}`);
  
  await adapter.disconnect();
}

/**
 * Demo 3: LocalStorage Adapter - Persistent Storage
 */
export async function demoLocalStorage() {
  console.log('\n=== LocalStorage Adapter Demo ===');
  
  const adapter = new JsonFileAdapter();
  
  await adapter.connect({
    storageType: 'localstorage',
    storageKey: 'demo-todos'
  });
  
  // Create todos
  await adapter.create('todos', {
    title: 'Buy groceries',
    completed: false,
    priority: 'high'
  });
  
  await adapter.create('todos', {
    title: 'Write documentation',
    completed: true,
    priority: 'medium'
  });
  
  // Query incomplete todos
  const result = await adapter.query('todos', {
    filters: [{ field: 'completed', operator: 'eq', value: false }],
    sort: [{ field: 'priority', desc: true }]
  });
  
  console.log('Incomplete todos:', result.data);
  console.log('Data persists in localStorage - reload page to verify!');
  
  await adapter.disconnect();
}

/**
 * Demo 4: Adapter Registry - Check Capabilities
 */
export function demoAdapterRegistry() {
  console.log('\n=== Adapter Registry Demo ===');
  
  // Get all registered adapters
  const adapters = adapterRegistry.getRegisteredAdapters();
  console.log(`Registered adapters: ${adapters.length}`);
  
  adapters.forEach(adapter => {
    console.log(`\n${adapter.name} (${adapter.type})`);
    console.log(`  Description: ${adapter.description}`);
    
    const capabilities = adapterRegistry.getCapabilities(adapter.type);
    console.log('  Capabilities:');
    console.log(`    CRUD: ${capabilities.create ? '✅' : '❌'} Create, ${capabilities.read ? '✅' : '❌'} Read, ${capabilities.update ? '✅' : '❌'} Update, ${capabilities.delete ? '✅' : '❌'} Delete`);
    console.log(`    Advanced: ${capabilities.relationships ? '✅' : '❌'} Relationships, ${capabilities.transactions ? '✅' : '❌'} Transactions, ${capabilities.offline ? '✅' : '❌'} Offline`);
    console.log(`    Search: ${capabilities.filtering ? '✅' : '❌'} Filtering, ${capabilities.sorting ? '✅' : '❌'} Sorting, ${capabilities.pagination ? '✅' : '❌'} Pagination`);
  });
}

/**
 * Run all demos
 */
export async function runAllDemos() {
  try {
    // Demo 4 first (synchronous)
    demoAdapterRegistry();
    
    // Demo 2: In-memory (fast)
    await demoJsonFile();
    
    // Demo 3: LocalStorage
    await demoLocalStorage();
    
    // Demo 1: REST API (requires internet)
    // Uncomment to test with real GitHub API
    // await demoRestApi();
    
    console.log('\n✅ All demos completed successfully!');
  } catch (error: any) {
    console.error('❌ Demo failed:', error.message);
    console.error(error);
  }
}

// Auto-run demos in browser console
if (globalThis.window !== undefined) {
  (globalThis as any).adapterDemos = {
    runAll: runAllDemos,
    restApi: demoRestApi,
    jsonFile: demoJsonFile,
    localStorage: demoLocalStorage,
    registry: demoAdapterRegistry
  };
  
  console.log('💡 Adapter demos loaded! Run in console:');
  console.log('  adapterDemos.runAll()     - Run all demos');
  console.log('  adapterDemos.jsonFile()   - In-memory storage demo');
  console.log('  adapterDemos.localStorage() - Persistent storage demo');
  console.log('  adapterDemos.registry()   - Show registered adapters');
}
