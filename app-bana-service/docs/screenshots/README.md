# Screenshot capture guide

Purpose
- Replace the placeholder SVGs in this folder with real PNG screenshots of your UI.
- The user guide prefers PNGs when available and falls back to the SVG placeholders.

Files to produce (PNG)
- datasource-add.png
- datasource-list.png
- datasource-test.png
- builder.png
- swagger.png

Quick capture on macOS (interactive)
- Ensure the app is running (default port 8080) and the pages render correctly.
- Use the helper script (prompts you through each page):

```bash
./scripts/capture_screenshots.sh
```

Notes
- The script opens each target page and asks you to click the browser window to capture it.
- Try to keep the browser window ~1200×700 for consistency.
- You can recapture any PNG by re-running the script and following the prompts for specific pages.
- If you prefer manual capture:
  - Press Shift+Cmd+5, choose “Capture Selected Window,” click the browser window, and save to the corresponding PNG filename in this folder.

Troubleshooting
- If the script cannot find the app pages, verify the app is running and the port is correct (export APPBANA_PORT to override).
- If images don’t show in the user guide, ensure the PNG filenames match exactly and that markdown references point to this folder.

