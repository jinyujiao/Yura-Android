# Yura Android

Clean Android rebuild of Yura on top of Readium Kotlin Toolkit.

## Structure

- `app/`: Yura application code. New Compose + Material 3 UI lives here.
- `readium/`: Readium Kotlin Toolkit source modules, kept as library code.
- `buildSrc/`, `gradle/`: Gradle infrastructure copied from the Readium project so the toolkit modules build unchanged.

## Product Scope

The new app keeps the core Yura features:

- Local EPUB import and bookshelf.
- EPUB reading through Readium navigator.
- Custom reader chrome and reader settings.
- TTS with system, MiMo, and Microsoft providers.
- Paragraph highlighting and TTS/media control synchronization.
- App settings, reader settings, TTS settings, About, and reserved WebDAV settings.

The old Readium test app layers are intentionally not copied:

- OPDS/catalog online reading.
- Demo reader fragments and XML navigation.
- Unused PDF/audio/image reader surfaces.
- Old test-app TTS controls.

## Migration Order

1. Wire local EPUB import and persistent bookshelf.
2. Embed `EpubNavigatorFragment` in the new reader shell.
3. Move Readium reader preferences into a clean settings bridge.
4. Port TTS engines and media session control.
5. Add paragraph extraction, text cleaning, highlighting, and auto-next-chapter.
6. Clean package names, resources, and old compatibility code.
