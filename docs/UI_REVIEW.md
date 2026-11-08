# UI preview and Figma comparison

## Run preview mode

Debug builds enable `BuildConfig.UI_TEST_MODE` by default. To disable it:

```powershell
.\gradlew.bat :app:assembleDebug -PuiTestMode=false
```

Release builds always set the flag to `false`. Preview fixtures are generated in memory by `PreviewContent`; they never overwrite cached remote JSON. Feature selections use `emoji_battery_preview` preferences. Blank cards retain stable IDs and click/favorite actions. Test mode bypasses billing and service activation for the preview actions; it does not grant a subscription.

Long-press **Home** in the bottom navigation to open the preview route picker. Individual screens can also be opened on the emulator:

```powershell
adb shell am start -n com.hd.emojibattery/.MainActivity --es target_screen 870:9122
```

The bundled catalog is now empty. `app_content.example.json` contains the previous sample schema/data for reference. Remote Config and last-known-good remote content remain available when preview mode is off.

## Comparison artifacts

- [Offline comparison gallery](ui-comparison/index.html): search by Figma ID or screen name; click either image for its original resolution.
- `figma-reference/870-*.png`: Figma exports, including the nested long content frames and text reference.
- `ui-comparison/870-*.png`: Pixel 8a screenshots in test mode.
- `ui-comparison/compare-870-*.png`: paired Figma/Android images.
- `ui-comparison/capture-results.json`: route launch and screenshot results.
- `ui-comparison/smoke-results.json`: actual tap-through flow checks.

There are 65 requested references (plus the nested blank-item reference 870:883): 60 screen/state frames, four nested content frames, and one text layer (`870:2983`). Splash was excluded from UI changes. Android screenshots include system bars and live battery readings, so these differ from the static Figma artwork. The long Language reference is represented by a scrolling Android list.

## Changes and validation

Latest refinement: Customize dashboard, native Gesture action sheet, Notch panels/sliders, Troll percentage editor, Charging controls/gallery/success sheet, Status Bar icon and date editors, Sticker adjustment controls, Settings/help, and onboarding/paywall were revised. Gesture and help illustrations, premium crown, Customize banner title, paywall title and offer ribbon are exported Figma assets. Feature layouts now use ViewStubs so only the active feature is inflated. Feature dialogs are dismissed when their fragment view is destroyed. The final visual review also corrected Gesture disabled-state spacing, Pet selection confirmation/removal, Sticker empty versus selected states, Search suggestions, and Charging main/gallery column counts and footer actions. Capture records include the actual APK SHA-256 and timestamp. Changed screens are recaptured; unchanged reviewed layouts retain their original capture hashes, with compatibility recorded separately in capture-compatibility.json and labeled in the gallery.


- First-launch routing now includes Language before Intro; language selection is persisted. The app still needs translated string resources for languages other than its existing resources.
- Intro artwork, copy, panel proportions, indicators, ad placeholder area, and actions follow the supplied frames. Paywall uses uncropped artwork, reachable scrolling content, selectable plans and a test-only Continue action. Billing interval labels were corrected to match their corresponding plans.
- Nunito font and the flags, Home/Customize illustrations, library/search artwork and notch artwork were exported/bundled for UI use.
- Added distinct Sticker selection/editor/library states, adjustment sheet, favorites deletion, Status Bar sections/pickers, date controls, Notch sliders, Troll catalogue/setup, and Settings feedback/restricted-app dialogs.
- Primary editor actions stay at the bottom; returning to a main tab clears obsolete editor back-stack entries.
- Fixed two existing missing XML layout dimensions that caused feature screens to render only a background. Fixed route-family matching that mixed Home with Status Bar and omitted the selected Sticker screen.

Build: `:app:assembleDebug`. Routing regression checks: `:app:testDebugUnitTest`. Emulator: Pixel 8a, Android 15 / API 35 (isolated EmojiBatteryReview AVD). The capture script opens each independent supplied screen/state; the smoke script taps through onboarding, Pet, Sticker, Customize, Settings and Charging. All eight smoke scenarios passed, including Status Bar Data Live Preview updates and Battery Troll percentage editing. Four unit tests passed. See the result JSON files for the recorded outcomes.

This is UI/flow validation using blank catalogue fixtures. It does not validate purchases, remote media playback, or real overlay behavior. The image gallery provides the actual visual result rather than a claim of exact pixel equivalence. Native system bars and device font rendering can differ from the static reference. Date font labels use the inspected Nunito 14sp design; font-preview choices still use Android typeface fallbacks.

After Figma MCP Go reconnected, ten Gesture action icons, four Paywall benefit icons, the Charging success illustration and the Sticker empty-state artwork were exported directly. Their node IDs and file hashes are recorded in `figma-reference/asset-manifest-latest.json`. The user’s disconnect rule is persisted in [AGENTS.md](../AGENTS.md): stop immediately if Figma MCP Go disconnects; do not invent or substitute UI while disconnected.

## Reproduce

Run from the repository root with the preview APK installed and emulator-5554 booted:

```powershell
python docs/scripts/capture_ui.py
python docs/scripts/smoke_ui.py
python docs/scripts/build_ui_report.py
```

`capture_ui.py` accepts a list of route IDs for selective recapture. `smoke_ui.py` accepts named flows; the onboarding scenario can replay Language and Intro in test mode. The scripts use ADB from the SDK path on this workstation. `build_ui_report.py` requires Pillow.
