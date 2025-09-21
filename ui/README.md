# AppbanaUi

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.2.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

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

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

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

## Quick build & run (workspace)

Use the repo-root scripts to build all libraries and the Studio app, and to run the SSR server:

```zsh
cd /Users/dilip/git/app-bana
./build.sh --clean   # or just ./build.sh for incremental
./run.sh --port 4000 # defaults to 4000; add --open on macOS
```

Alternatively, from the repo root via npm scripts:

```zsh
npm run ui:build
npm run ui:run -- --port 4000
```

SSR-only (from the UI workspace after a build):

```zsh
cd /Users/dilip/git/app-bana/ui
npm run serve:ssr:studio
```

Notes

- The Studio app includes Google Material Icons via a link tag in `projects/studio/src/index.html`, enabling `<mat-icon>` ligatures.
- Library build order and workspace paths are managed by the root `build.sh`.
- See also: `llms-angular.txt` (agent guardrails/execution patterns for Angular work) and `angular-best-practices.md` (coding, style, architecture). Follow both alongside `docs/STYLE_GUIDE.md` when implementing UI changes.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
