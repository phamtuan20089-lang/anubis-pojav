# Anubis on PojavLauncher / Android (experimental, untested on device)

## What this fork changes
- `Platform` helper detects Pojav-family launchers (env vars `POJAV*`, `pojav*`/`glfwstub*` properties). Override with `-Danubis.forceAndroid=true` / `-Danubis.forceDesktop=true`.
- ImGui init is wrapped: if the natives can't load, Anubis disables its UI instead of crashing the game. The ClickGUI screen then shows a notice and closes with ESC.
- Borderless-fullscreen mixin is skipped on Android (GLFW window APIs are stubs there).
- The "disable direct state access" mixin now also applies on mobile/translated GL renderers (gl4es, Mali, Adreno, ANGLE, PowerVR).
- AWT-drawn sky textures (SkyEmoji / SkyLyrics / Peep) turn off if `java.awt` is not usable in the launcher's JRE.

## Mobile ClickGUI (works without ImGui)
When ImGui is unavailable, Anubis opens `MobileClickGuiScreen`, built only from vanilla widgets. It drives the same modules and settings, so every module and saved config keeps working.
- Open it from the **Anubis** button (top-left) on the pause menu or title screen, or with the Anubis GUI key if you map one in the launcher controls.
- Tap a category, tap a module name to see its settings, tap the ON/OFF pill to toggle. Drag to scroll, drag a slider to change a number.
- Supported: boolean, mode, number, key, color (tap cycles a preset palette), text (tap to edit), action buttons, module keybind and hold mode.
- Config, Profile and Theme (copy/paste/reset) are available through the CONFIGS / PROFILE / SETTINGS buttons (`MobileConfigScreen`; profiles are shared via clipboard code, no file dialogs). Not available yet: block/item/string list editors, the HUD layout editor and per-surface theme editing. There is no emote feature in the original Anubis.

## The remaining hard blocker for the original UI: ImGui natives
The whole UI (ClickGUI, HUD, toasts) is drawn with imgui-java, and the bundled natives are Windows/Linux/macOS desktop builds only. They cannot load on Android (bionic, aarch64).
You need an Android arm64 build of the imgui-java 1.90.0 JNI library:
1. Build it from the imgui-java repository with the Android NDK (aarch64).
2. Name it `libimgui-java64.so` and place it in `<game dir>/config/anubis/natives/`, or start the JVM with `-Dimgui.library.path=<dir>`.
3. Verify the exact property and file names against the imgui-java 1.90.0 source, since they were not checked in this environment.
Without it the mod loads but shows no UI. The alternative is rewriting the UI on vanilla widgets, which is a large job.

## Using it in Pojav
- Minecraft 1.21.11 + Fabric Loader 0.19.2+, Fabric API 0.141.2+1.21.11, Java 21 runtime in the launcher.
- Pick a renderer with OpenGL 3.3 core features (e.g. MobileGlues, Holy-GL4ES, ANGLE or Zink depending on your launcher). Anubis' custom shaders may not work on every translation layer.
- Touch has no keyboard: bind the ClickGUI key to an on-screen control button in the launcher's control editor.
- Spotify integration needs desktop tools and won't work.

## Build
`./gradlew build` (JDK 21). Output: `build/libs/anubis-*.jar`. GitHub Actions builds it on every push.
Local builds are unsigned, so the official-build tamper check reports "unsigned local build" and does not stop the game.

## License
GPL-3.0-only, inherited from the original Anubis client by 4ldenz. Keep the LICENSE file and original credits when you publish a fork.
