# Studio (Angular SSR)

Quick start

- Build all libraries + Studio and run the SSR server from the repo root:

```zsh
cd /Users/dilip/git/app-bana
./build.sh --clean        # or ./build.sh for incremental
./run.sh --port 4000 --open
```

- Alternatively, from the UI workspace after a build:

```zsh
cd /Users/dilip/git/app-bana/ui
npm run serve:ssr:studio
```

Notes
- Default port is 4000; override with `--port <n>` in `run.sh` (or `PORT=<n>` for the npm script).
- `projects/studio/src/index.html` includes Google Material Icons to enable `<mat-icon>` ligatures.
- Libraries used in Studio (designer, runtime, ui-material, ui-schema) are built by the repo root `build.sh` in the correct order.

