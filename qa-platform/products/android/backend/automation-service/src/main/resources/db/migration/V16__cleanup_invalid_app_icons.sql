-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  V16 — clean up adaptive-icon XML mislabelled as image/png           │
-- │                                                                       │
-- │  Pre-fix ApkMetadataReader trusted apk-parser's icon ordering and    │
-- │  hardcoded image/png as the MIME for anything that wasn't .webp. For │
-- │  Android 8+ APKs the parser often returned adaptive-icon binary XML │
-- │  drawables — bytes that aren't a PNG, can't be rendered by a browser, │
-- │  and showed up in the UI as broken-image icons.                      │
-- │                                                                       │
-- │  ApkMetadataReader now magic-byte checks the payload before          │
-- │  generating the data URL. This migration nulls rows that the old     │
-- │  parser polluted so the next APK upload repopulates them via the     │
-- │  fixed path (AppService.upload sets iconData when it's null).        │
-- │                                                                       │
-- │  Detection: real PNG/JPEG/WebP/GIF base64 always starts with a       │
-- │  known prefix (89 50 4E 47 → "iVBORw0KGgo", FF D8 FF → "/9j/",       │
-- │  "RIFF" → "UklGR", "GIF8" → "R0lGOD"). Anything else was bogus.      │
-- └──────────────────────────────────────────────────────────────────────┘

UPDATE android_automation.apps
SET icon_data = NULL
WHERE icon_data IS NOT NULL
  AND NOT (
       icon_data LIKE 'data:image/png;base64,iVBORw0KGgo%'
    OR icon_data LIKE 'data:image/jpeg;base64,/9j/%'
    OR icon_data LIKE 'data:image/webp;base64,UklGR%'
    OR icon_data LIKE 'data:image/gif;base64,R0lGOD%'
  );
