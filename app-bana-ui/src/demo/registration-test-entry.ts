/**
 * Registration Test Entry Point
 * Loads all necessary modules for the user registration test
 */

// Initialize adapter system
import { registerBuiltInAdapters } from '../core/adapter-bootstrap';

// Load user registration test module (makes demo functions available)
import { UserEntity } from '../demo/user-registration-test';

// Load the registration form component
import '../components/UserRegistrationForm';

registerBuiltInAdapters();

console.log('🚀 User Registration Test loaded');
console.log('📋 UserEntity available in console');
console.log('🧪 userRegistrationDemo available in console');
console.log('\n💡 Try: await userRegistrationDemo.runFullDemo()');

// Make UserEntity accessible from console
if (globalThis.window !== undefined) {
  (globalThis.window as any).UserEntity = UserEntity;
}
