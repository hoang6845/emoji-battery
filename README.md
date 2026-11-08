## Remote data and Apply runtime

See [REMOTE_DATA_README](docs/REMOTE_DATA_README.md) for the current Firebase keys, JSON filenames, implemented Apply flows and remaining data/device validation. This supersedes the historical catalog and outstanding-feature notes below. The latest runtime changes have not been built or tested, as requested.

# Emoji Battery Android

UI preview update: see [UI review, test-mode instructions and emulator comparison](docs/UI_REVIEW.md). The comparison gallery contains the current Figma exports and Pixel 8a captures. The older implementation notes below describe the initial baseline.

Android app for the Emoji Battery Figma flow. It uses one `MainActivity`, one `EmojiBatteryApplication`, AndroidX Fragments, XML layouts, the shared `codeBase` base classes, and the separate `monetization` module. Figma IDs and route mapping are listed in [the screen inventory](docs/FIGMA_SCREEN_INVENTORY.md).

## Architecture

- `activity_main.xml` hosts the Fragment container and includes `app_bottom_nav.xml`; the Activity connects tab actions and system permission flows.
- Splash, intro, welcome-back and paywall inflate XML layouts and inherit `BaseSplashFragment` or `BaseFragment` with a `BaseViewModel`. Their illustrations are exported Figma image layers in `app/src/main/res/drawable/`.
- Home, Pet, Customize, Status Bar, Charging, Sticker and Settings are Kotlin Fragments with Kotlin ViewModels inheriting `BaseViewModel`. Each supplied Figma frame ID routes to a concrete Fragment and frame-specific ViewModel in `ui/routes/FrameFragments.kt` and `FrameViewModels.kt`. Related states share XML feature layouts and switch the relevant XML sections by route ID; screen structures are declared in XML, with native repeated controls and adapters for palettes and choices.
- `codeBase` contains the shared lifecycle classes. ViewModel and ViewBinding reflection now resolve types through an intermediate feature base class. `monetization` remains a separate Android library module.
- `ScreenRouter` maps supplied Figma frame IDs to concrete frame Fragments. Each frame Fragment has its own ViewModel; feature families reuse XML sections and adapters for repeated catalogue controls.
- `AppContentRepository` serves bundled/cached JSON and can refresh from a GitHub raw URL supplied through Firebase Remote Config. `RemoteImageLoader` fetches HTTPS image URLs.
- `BatteryMonitorService`, `PetOverlayService` and `StatusBarOverlayService` implement charging notification/monitoring and the two user-triggered overlay services.
- App source code is Kotlin; screen and overlay views are defined in XML. English UI resources start in `app/src/main/res/values/strings.xml`.

## Figma coverage

The supplied Figma IDs were read with Figma MCP. The inventory records layer names, dimensions and current route mapping. The supplied frames have PNG reference exports in `docs/figma-reference/`, named `870-<node-id>.png`; additional nested content references are included. `870:2983` is a text layer, not a screen. The long 870:7292, 870:7701, 870:8110 and 870:8531 frames are also exported for reference. The charging preview background was exported from its Figma image layer as `charging_scene.png`.

The PNGs are Figma references. Screen layouts use exported art layers where available; `charging_scene.png`, intro art and the home banner are used by the UI. Home, pet selection and permission, gesture setup, charging preview, battery preview, paywall and onboarding have XML layouts shaped from their references. The updated comparison gallery records actual emulator captures and marks any image not verified against the latest APK. See docs/UI_REVIEW.md for validation and remaining limits.

## GitHub content workflow

The bundled fallback is `app/src/main/assets/app_content.json`. Host the same JSON schema and artwork at a public HTTPS URL, then set Firebase Remote Config string `emoji_battery_content_url` to the raw JSON URL (for example, a GitHub raw URL). Publish JSON and image assets first, then publish Remote Config. When Firebase is not initialized, the app uses the bundled/cached content. Valid remote content is cached; invalid JSON, unsupported schema or network failure leaves the previous content available. Refresh is limited by the Remote Config minimum fetch interval.

Keep item IDs stable because user favorites/selections refer to IDs. Increase `content_version` for catalog updates. Increase `schema_version` only when the app parser is updated to accept the new schema. Use HTTPS for remote media; empty URLs use the local emoji/color fallback.

| JSON field | Displayed in | Important item fields |
| --- | --- | --- |
| `themes[]` | Home theme catalogue, charging theme selection/preview | `id`, `name`, `category`, `emoji`, `color`, `free`, `wallpaper` |
| `stickers[]` | Home gallery, search, add/edit sticker, view more, detail, favorites | `id`, `name`, `category`, `emoji`, `free`, `image_url` |
| `pets[]` | Pet home/view more/detail and floating pet selection | `id`, `name`, `category`, `emoji`, `free`, `animation_url` |
| `emojis[]` | Battery/Status Bar emoji selectors and previews | `id`, `name`, `emoji`, `free` |
| `battery_styles[]` | Status Bar battery style selectors | `id`, `name`, `emoji`, `free` |
| `animations[]` | Charging animation selector | currently string names, e.g. `"Pulse"` |
| `wallpapers[]` | Charging wallpaper selector and preview | `id`, `name`, `color`, `image_url` |
| `links` | Settings legal/support actions | `privacy_policy`, `terms`, `support` |

`schema_version` 1 and 2 are currently accepted. Keep each array present even when it is empty. See `app_content.json` for a valid example. Images for `pets[].animation_url` are currently displayed as still images; the player/animation format is not implemented.

## Implemented and outstanding

Implemented: one MainActivity and one Application; splash routes into onboarding on first launch; four intro pages use Figma illustrations and persist completion; paywall plan selection and close navigation are wired; Figma reference PNGs and art layers are exported; feature Fragments/ViewModels inherit `codeBase`; feature UI is Kotlin with XML layouts; Home/Pet/Sticker/Customize/Charging/Status Bar/Settings interactions and route-specific screens are wired. Charging monitor notification permission, overlay permission flow, pet dragging, bundled JSON, last-known-good cache and Remote Config URL refresh are present.

Still outstanding: refine remaining feature states screen-by-screen from the Figma exports; compare screenshots on an emulator and tune typography/insets; connect Google Play Billing products and restore purchases (the paywall CTA reports that products are not configured); play animated pet/charging media; configure Firebase app credentials and Remote Config; review permission and charging flows on devices. This task intentionally did not build or run tests.

## Build

Build and device review were not run for this task, as requested. The Figma reference exports are ready for visual QA.
