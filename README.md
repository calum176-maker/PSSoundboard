# 🎮 PS Soundboard & Live Voice Changer

An advanced, zero-latency Android soundboard and real-time voice changer designed specifically for mobile gaming, party chat (such as the PlayStation App), and live audio broadcasting via a 3.5mm TRRS hardware loopback cable.

---

## ✨ Key Features

### 1. 🎙️ Live Voice Changer & Celebrity Presets
Transform your voice in real time and spew modified audio straight into party chat:
- **Instant Celebrity & Character Presets**:
  - **Arnold (Terminator)** (`🦾`): Deep action hero pitch shift (-4.5 st) + saturation drive.
  - **Darth Vader** (`🦹`): Dark lord pitch shift (-7.5 st) + robot ring-modulation + echo.
  - **Morgan Freeman** (`🎙️`): Warm narrator pitch shift (-3 st) + rich echo.
  - **Dark Knight (Batman)** (`🦇`): Gritty hero pitch shift (-6 st) + drive.
  - **Gordon Ramsay** (`👨‍🍳`): Aggressive radio-filtered broadcast voice + drive.
  - **Master Yoda** (`🟢`): Wise sage pitch shift (+4.5 st) + gentle echo.
  - **Classics**: Chipmunk, Deep Voice, Demon, Robot, Pit Radio, Alien, Cave Echo, and Natural.
- **Fine-Tuning Controls**: Adjustable pitch semitones, robot ring-mod frequency, pit-radio band-pass filter, saturation drive, echo mix, mic input gain, and noise gate threshold.
- **Record, Test & Spew**: Record voice clips with active voice FX, audition them, spew them instantly into party chat, or save them to your vault.

### 2. 🎮 Zero-Latency Soundboard Engine
- Built on Android's `SoundPool` for instant, zero-latency playback.
- **Hard-Panned Audio Routing**: Automatically routes audio hard-left (`Tip -> 10k resistor -> Sleeve`) so the PlayStation App party chat receives your soundboard clips cleanly on the mic input while keeping right channel isolated.
- **Playback Modes**: One-Shot, Hold-to-Play (loop while holding), and Restart-on-Spam.

### 3. 🌐 Cloud Sound Library & Instant Search
- Integrated search across internet memes, movie quotes, and gaming sound clips (powered by MyInstants API with 10-at-a-time pagination and infinite scroll).
- Curated quick-search tags for Movies, Gaming, Memes, and Characters (Terminator, Batman, Gordon Ramsay, Star Wars, etc.).

### 4. ⚡ Master Spew Vault & Clip Creator
- Automatically remembers every sound you spew, preview, or record into a master vault.
- Pin favorites, save to downloads, or copy descriptions.
- Built-in Clip Creator to record from the phone's built-in mic or trim audio files.

### 5. 🪟 Floating Mini-Deck Overlay
- Floating overlay service allowing you to trigger your favorite soundboard pads while playing games or using other apps.

### 6. 🔌 Hardware Guide & Calibration Tone
- Built-in wiring schematic and step-by-step guide for constructing the custom 3.5mm TRRS loopback plug.
- 1 kHz calibration tone toggle to verify your loopback and party chat connection.

---

## 🔌 Hardware Setup (TRRS Loopback Plug)
To feed your phone's soundboard and voice changer into a PlayStation party chat:
1. Construct a 3.5mm TRRS breakout plug bridging **Tip (Left Audio Out)** through a **10kΩ resistor** to **Sleeve (Microphone In)**.
2. Plug the loopback cable into your phone's headphone jack (or USB-C to 3.5mm DAC).
3. Open the PlayStation App on your phone, join a party voice chat, and test using the Hardware Guide calibration tone or Voice FX.

---

## 🛠️ Tech Stack
- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose & Material 3 (Dark Slate Theme)
- **Architecture**: MVVM, StateFlow, Coroutines
- **Audio**: SoundPool, AudioRecord, AudioTrack, custom DSP audio effects processor (`VoiceFxProcessor`)

---

## 🤝 Collaboration
Built by [calum176-maker](https://github.com/calum176-maker) and friends. 

To contribute:
1. Clone the repository:
   ```bash
   git clone https://github.com/calum176-maker/PSSoundboard.git
   ```
2. Open the project in **Android Studio**.
3. Create a feature branch (`git checkout -b feature/amazing-feature`), commit your changes, and push to GitHub!
