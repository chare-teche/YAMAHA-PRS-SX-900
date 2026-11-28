# ETHIO YAMAHA - master plan and honest status

Reference: the SX900-style console image is the permanent main interface. Everything is drawn as real Compose parts (no picture as background).
Status words: DONE = works and (where possible) tested, PARTIAL = exists but incomplete, TODO = not built yet, BLOCKED = needs something from the owner.

## Architecture (what is in the project today)
```
com.example
  MainActivity.kt                 entry
  ui/Console.kt                   console: speakers, left controls, LCD, right controls, style strip, piano
  ui/Components.kt                HwButton, PanelButton, Knob, DataWheel, PianoKeyboard, wheels
  ui/Screens.kt                   LCD pages: Voice, Style, Slider, Multi Pad, Registration, Settings, User (open INSIDE the LCD area)
  viewmodel/ArrangerViewModel.kt  one shared state: buttons, LCD, keyboard and engine all use it
  audio/Audio.kt                  AudioTrack engine, 64 melodic + 16 drum voices, sequencer
  audio/DrumSamples.kt            real drum samples loader (ethio_drums.bin)
  audio/MusicStudioKit.kt         .Instrument drum kit reader
  audio/DrumMap.kt DrumKit.kt     MIDI drum notes, kit tuning (crash muted)
  audio/Styles.kt                 built-in styles, chord detection (auto, 1/2/3 key)
  formats/                        .STY parser, converter, file inspector, library
```
Target folders from the blueprint (ui/lcd, ui/settings, data/...) are a later tidy-up. Moving files has no sound benefit and breaks a working build, so it is done last and only if wanted.

## Blueprint parts - status
| Part | Item | Status | Note |
|---|---|---|---|
| 1 | Reference look, speakers, LCD, knobs, keys | PARTIAL | panel rebuilt; missing keys listed below |
| 1 | No red lights | DONE | indicators blue/green/orange/gold |
| 2/3 | Settings and browsers inside the LCD | PARTIAL | pages now open over the control area (LCD grows); speakers, style strip and piano stay visible and playable |
| 3 | Button to LCD map | PARTIAL | MENU, EXIT, STYLE, VOICE, SLIDER, MULTI PAD, REGIST, TEMPO exist; RECORD, MODE, TAB, MIDI, METRO, SPLIT/MIX, CHORD MEMORY, CHORD SCAN, MY SET, Q.T. still TODO |
| 4 | Settings tree | PARTIAL | keyboard size/black keys/buffer exist; Language, Theme, MIDI, Display, Storage, Backup are TODO |
| 5 | Rhythm browser | PARTIAL | categories, P1-Pn pages, + import; Search, Favorites, Recent, Preview TODO |
| 6 | Voice and instrument browser | PARTIAL | 13 built-in synth voices, P pages, + opens import; sampled pitched voices BLOCKED (see below) |
| 7 | 4 parts RIGHT1-3 + LEFT | PARTIAL | each part has voice, volume, octave, on/off; per-part pan and effects TODO |
| 8 | Piano customisation | PARTIAL | visible keys, black key size, split exist; white key length/width, velocity curve, sensitivity TODO |
| 9 | Import | PARTIAL | .STY and folders DONE; .Instrument drum kits DONE; Music Studio Rhythm .Style DONE (notes, sections, tempo; read on 5 real files; time signature assumed 4/4; embedded samples not played); .SET BLOCKED |
| 10 | Audio engine | PARTIAL | real drum samples DONE; crash muted by request; melodic voices are synthesized, no sample playback yet |
| 12 | Persistence | PARTIAL | settings, registrations, imported styles, drum kit saved; favorites/recent TODO |

## What the owner must supply (BLOCKED items)
1. Two or three real ORG `.style` files -> to read the format honestly and write the importer.
2. A real `.set` file (if needed) -> same reason.
3. For pitched voices: 1-2 pitched `.Instrument` files (not drum kits) so the note/key-range layout can be checked.

## Next stages (one at a time, each tested before the next)
1. LCD finish: category column + search + favorites + recent in Style and Voice browsers; Drum browser page (kits, assign, preview); Registration rename/lock.
2. Missing panel keys that have a real function: SPLIT, METRO (metronome click), CHORD MEMORY, MY SET, MODE.
3. Settings tree completion with real effects: Display (text size, LCD brightness), Keyboard (white key size, velocity curve), Drums (per-drum level), Storage (library sizes, delete).
4. .Style: read time signature, embedded voice samples, instrument-setting blocks.
5. Sample-based pitched voices (.Instrument pitched, then SF2) with 64-voice polyphony.
6. Per-part pan and effects, mixer persistence, MIDI input (USB keyboard).
7. Tidy-up of folders to the blueprint layout if still wanted.

## Rules kept
Tests are never weakened. A feature is marked DONE only after it was built and its tests ran. Unsupported files are reported, never faked.
