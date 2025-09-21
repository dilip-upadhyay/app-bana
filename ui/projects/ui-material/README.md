# UiMaterial

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.0.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the library, run:

```bash
ng build ui-material
```

This command will compile your project, and the build artifacts will be placed in the `dist/` directory.

### Publishing the Library

Once the project is built, you can publish your library by following these steps:

1. Navigate to the `dist` directory:
   ```bash
   cd dist/ui-material
   ```

2. Run the `npm publish` command to publish your library to the npm registry:
   ```bash
   npm publish
   ```

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.

## Public API (wrapper components)

All wrapper components are exported from the library's public API barrel at `src/public-api.ts`.
You can import them directly from `ui-material`:

```ts
import { AbButton, Icon } from 'ui-material';
```

Example usage in a standalone component:

```ts
import { Component } from '@angular/core';
import { AbButton, Icon } from 'ui-material';

@Component({
  selector: 'demo-material-usage',
  standalone: true,
  imports: [AbButton, Icon],
  template: `
    <ab-icon name="home"></ab-icon>
    <ab-button color="primary">Click me</ab-button>
  `,
})
export class DemoMaterialUsage {}
```

Available wrappers (selectors)
- Button: `<ab-button>`
- Icon: `<ab-icon>`
- Select: `<ab-select>`
- Checkbox: `<ab-checkbox>`
- Radio: `<ab-radio>`
- Slide toggle: `<ab-slide-toggle>`
- Datepicker: `<ab-datepicker>`
- Card: `<ab-card>`
- Toolbar: `<ab-toolbar>`
- Tabs: `<ab-tabs>`
- Input: `<ab-input>`

Note: The Studio app includes the Google Material Icons stylesheet in its `index.html` so `<mat-icon>` ligatures work when used there.
