# ETHIO YAMAHA (rebuild v2)

Independent Android arranger keyboard, inspired by the performance experience of professional
arranger workstations. NOT official Yamaha software.

Kotlin + Jetpack Compose. Application ID: com.aistudio.ethioyamaha.kzrtxm

## Build the APK from your phone (GitHub does the compiling)

1. Create a free GitHub account and a NEW repository (any name, for example EthioYamaha).
2. Upload ALL files of this project, keeping the folders:
   - Easiest on a phone: open the repository in a browser, choose Add file > Upload files,
     and upload the files folder by folder. If your phone cannot upload folders, use
     Add file > Create new file, type the full path (for example
     app/src/main/java/com/example/MainActivity.kt) and paste the code.
   - The file app/debug.keystore is binary: upload it with Upload files, do not paste it.
   - The hidden folder .github/workflows/build.yml MUST be uploaded or no build will start.
3. Open the Actions tab. The workflow "Build APK" starts by itself (or press Run workflow).
4. When it finishes (green tick, about 5-8 minutes) open the run and download the
   artifact "EthioYamaha-debug-apk" (a zip containing app-debug.apk).
5. Unzip it, install app-debug.apk. If an older ETHIO YAMAHA is installed with a different
   signature, uninstall it first (this deletes its saved data).

If the build is red, open the failed step and copy the first lines marked "e:" - those are
the compile errors - and send them for fixing.

## Project layout

- app/src/main/java/com/example/MainActivity.kt     app start
- .../audio/Audio.kt        synth engine, voices, sequencer, reverb, limiter
- .../audio/Styles.kt       sections, styles, chord detection, voice list
- .../viewmodel/ArrangerViewModel.kt   state and all button actions
- .../ui/Components.kt      buttons, knobs, wheels, piano keyboard
- .../ui/Console.kt         main SX900-style screen
- .../ui/Screens.kt         Voice, Style, Mixer, Multi Pad, Registration, Settings pages

## Status

Written, brackets and cross-file names checked. NOT yet compiled and NOT yet tested on a phone.
Voices are synthesized, not recorded samples. STY/ORG import, MIDI, recording, SoundFont and
the worship workspace are not included yet.

## Yamaha .STY import (v11 work)
- `formats/MidiFile.kt` reads standard MIDI files safely (bounds checks, running status, size limits).
- `formats/StyParser.kt` reads Yamaha SFF1/SFF2 styles: sections, CASM channel rules, One Touch Settings.
- `formats/StyleConverter.kt` converts them to the engine's `Style` (96 steps per 4/4 bar, absolute notes).
- The engine converts imported notes to the played chord (`convertAbs` in Audio.kt). Built-in styles are unchanged.
- USER page (LCD menu): add folders, browse sub-folders, tap a `.STY` to import and select it.
- Not yet supported (recognised in the browser, with a message): ORG `.Style`, `.mid`, `.pad`, `.RGT`, `.SET`, `.Instrument`.
- Unit tests: `app/src/test/java/com/example/formats` with real style files; `tools/verify_sty.py` is the independent reference reader.

## Upgrade 1 (audit steps 0-3): CI safety, drum sounds, drum voices
- `.github/workflows/build.yml`: no source unpacking, no auto-push, read-only token; runs on main, import-sty, PRs; unit tests are blocking (the APK step still runs).
- `audio/DrumMap.kt`: full percussion map (GM + Yamaha extras). Old blip notes now sound like shaker, cabasa, claves, triangle, etc.
  Notes 24-27, 29, 30 and out-of-range notes are silent and counted (`drumSilentHits`) instead of playing a 600 Hz blip.
- `Audio.kt`: 48 drum voices (was 16), quietest-voice stealing, per-kind life limit with a 4 ms fade, choke fade for hats,
  1.2 ms attack ramp on noise drums, keyboard all-off no longer cuts style drums, new `Op.PANIC` (used when the app goes to the background).
- Tests: `DrumMapTest`, `DrumRenderTest` (offline render of the real engine). `tools/drum_sim.py` is a Python model used to measure the change on the four real styles.

## Upgrade 2: drum kits and drum import report
- `audio/DrumKit.kt`: the kit program found on a style's drum channel (XG numbers 0/1 Standard, 8 Room, 16 Rock, 24 Electro, 25 Analog, 32 Jazz, 40 Brush, 48 Classic) now tunes the synthesized drums (pitch and decay). Product-specific kit numbers (35, 36, 80, 87, 88, 91 ...) are reported as unknown and use Standard. These are synthesized variants, NOT the original Yamaha kits.
- `audio/DrumReport.kt`: the import report (USER page info) now lists drum hits and the notes that have no sound, e.g. "note 24 x74". On the 30 bundled real styles 1,882 of 40,439 rhythm hits (4.6%) use notes 19-30 that belong to the keyboard's own drum kit; they stay silent until the real kit samples are available.
- Not compiled or run in the environment that wrote this change: the CI unit tests (DrumKitTest, DrumReportTest, StyDrumReportTest) are the first check.

## Upgrade 3: ETHIO YAMAHA deck, drum sound fixes, file inspector
- Layout: keyboard 75% of the height (setting "Keyboard height", 40-80%, saved as `keyHeight75`), control deck 25%: header, master/LCD/shortcuts row, and a 3-page strip (STYLE / PARTS / MEMORY). No microphone or speaker hardware drawn. There is no Song button because song playback does not exist.
- Octave per part is now -6..+6 (was -2..+2).
- Crash: was high-passed white noise ("shhhh"); now 6 inharmonic partials + a shorter noise burst. Cowbell: own sound (540 + 800 Hz square-ish pair, short). Congas and bongos: own sound at realistic low pitch (230-420 Hz) with a pitch drop. They used to be the same 520-820 Hz sine blip.
- `formats/FileInspector.kt`: ORG .style/.template/.music/.set and Yamaha .SET are read and their real structure shown (chunks, ZIP entries, magic bytes). Originals are copied unchanged to `files/library/inbox`. A MIDI file with a CASM chunk is imported as a style whatever its extension. No ORG/SET format is guessed.
- Not compiled or run where it was written: CI unit tests are the first check.

## Upgrade 4: sound quality and look (from a screen recording of the app)
Findings from the recording (34 s, Disco style): no clipping, no dropouts, no underruns; 65-69% of the energy below 500 Hz and only 4% above 2 kHz (muddy, dull); 40-77 voices sounding for one style (pool nearly full).
- `audio/Tone.kt`: master tone EQ (FLAT / CLEAR / WARM), 4 biquads on the final mix, saved setting `tone`, default CLEAR. On the recording CLEAR moves energy below 500 Hz from 69% to 55% and above 2 kHz from 9% to 19%.
- Voice pool: released tails fade twice as fast when more than 36 melodic voices sound; freeing threshold raised from 0.0006 to 0.002 (about -67 dBFS, inaudible).
- Hi-hats: metal partials plus less noise (flatness 0.53 -> 0.16 closed, 0.54 -> 0.23 open in the numeric port).
- Look: glowing blue frames, brushed-metal deck, gold crown title, glossy keys with blue press glow, glow ring on knobs.
- Not compiled or run where it was written; nothing here was heard on a phone.

## Upgrade 5: panel restored to the SX900-style layout
The deck redesign was replaced by the original panel (magenta LCD title bar, blue/orange menu tiles, data wheel, style strip with group titles) because it matches the reference photo and its text is not clipped. The LCD title bar now reads "ETHIO YAMAHA  Main Menu". Keyboard height defaults to 51% of the screen (reference photo: panel 37%, strip 12%, keys 51%); the Settings slider ranges 35-75%.
