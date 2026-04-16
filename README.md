# 📱 DeviceLog

> **Your phone, always at its best.**  
> DeviceLog is a lightweight Android application built to help you monitor, maintain, and optimize your device's performance — all from one clean, dark interface.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Building from Source](#building-from-source)
- [Project Structure](#project-structure)
- [Usage](#usage)
- [Permissions](#permissions)
- [Contributing](#contributing)
- [Roadmap](#roadmap)
- [License](#license)

---

## 🔍 Overview

**DeviceLog** is an Android maintenance and monitoring app built entirely in **Java** using **Android Studio**. It gives users real-time telemetry on their device's health — battery current draw, RAM load, storage usage, uptime, and temperature — all displayed in a sleek, dark dashboard.

Toggle monitoring on or off at any time, check your device's real-time health status, and submit bug reports directly from within the app. DeviceLog keeps things fast, minimal, and always on point.

> Tested on **Samsung Galaxy S22 (SM-S901W)** running Android.

---

## ✨ Features

### 🔋 Battery Telemetry
- Large circular gauge showing live battery percentage
- Charge state display (Charging / Discharging)
- Real-time current draw in **mA** (e.g. -99 mA while discharging)
- Color-coded arc that reflects remaining charge level

### 📊 Live Stat Cards
- **RAM Load** — current memory usage as a percentage
- **Storage** — internal storage used vs. total available
- **Uptime** — how long the device has been running (e.g. 29h 35m 45s)

### 🏥 Real-Time Health Status
- Overall system health rating (e.g. **OPTIMAL**)
- Live device temperature readout in °C
- Green progress bar reflecting thermal and performance status
- Plain-English status summary (e.g. *"All systems operating at peak efficiency."*)

### 🎛️ Monitoring Toggle
- Single toggle switch in the top toolbar to enable or disable live monitoring
- Status label updates to **ACTIVE** / **INACTIVE** in real time

### 🐛 Bug Reporting
- Built-in **Submit a Bug** button at the bottom of the dashboard
- Lets users report issues without ever leaving the app

### 🎨 UI & Design
- Dark theme throughout — easy on the eyes and the battery
- Cyan / teal accent colors on cards, progress bars, and the battery arc
- Hamburger menu (☰) for navigation
- Device identifier displayed in footer (DeviceLog Telemetry • [Device Model])

---

## 📸 Screenshots

### Home Dashboard

<p align="center">
  <img src="screenshots/dashboard.jpg" alt="DeviceLog Home Dashboard" width="320"/>
</p>

> Dark dashboard showing a 30% battery at -99 mA discharge, RAM Load at 62%, Storage at 86%, Uptime at 29h 35m 45s, and an OPTIMAL health status at 24.6°C.

---

## 🛠️ Tech Stack

| Technology | Details |
|---|---|
| **Language** | Java |
| **IDE** | Android Studio |
| **Min SDK** | Android 8.0 (API 26) |
| **Target SDK** | Android 14 (API 34) |
| **Build System** | Gradle |
| **Architecture** | Activity-based (MVC) |
| **Tested Device** | Samsung Galaxy S22 (SM-S901W) |

---

## 🚀 Getting Started

### Prerequisites

Before you begin, make sure you have the following installed:

- [Android Studio](https://developer.android.com/studio) (Hedgehog or newer recommended)
- Java Development Kit (JDK 11 or higher)
- Android SDK with API level 26+
- A physical Android device **or** an Android emulator

---

### Installation

#### Option 1 — Install the APK directly

1. Download the latest `.apk` from the [Releases](../../releases) page.
2. On your Android device, go to **Settings → Security** and enable **Install from Unknown Sources**.
3. Open the downloaded APK and follow the on-screen prompts to install.

#### Option 2 — Build from Source

Follow the steps below.

---

### Building from Source

1. **Clone the repository**

```bash
git clone https://github.com/your-username/DeviceLog.git
cd DeviceLog
```

2. **Open in Android Studio**

   - Launch Android Studio
   - Select **File → Open**
   - Navigate to the cloned `DeviceLog` folder and open it

3. **Sync Gradle**

   Android Studio will automatically prompt you to sync Gradle. Click **Sync Now** if it doesn't start automatically.

4. **Run the app**

   - Connect a physical device via USB (with USB Debugging enabled) or start an emulator
   - Click the **▶ Run** button or press `Shift + F10`

---

## 📁 Project Structure

```
DeviceLog/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/yourpackage/devicelog/
│   │   │   │   ├── activities/        # All Activity classes
│   │   │   │   ├── adapters/          # RecyclerView & list adapters
│   │   │   │   ├── models/            # Data models / POJOs
│   │   │   │   ├── services/          # Background monitoring services
│   │   │   │   ├── utils/             # Helper & utility classes
│   │   │   │   └── MainActivity.java  # App entry point / main dashboard
│   │   │   ├── res/
│   │   │   │   ├── layout/            # XML UI layouts
│   │   │   │   ├── drawable/          # Icons, circular gauge assets
│   │   │   │   ├── values/            # Strings, colors (dark theme), themes
│   │   │   │   └── menu/              # Hamburger nav menu XML
│   │   │   └── AndroidManifest.xml
│   │   └── test/                      # Unit tests
│   └── build.gradle
├── screenshots/                       # App screenshots for README
├── gradle/
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 📖 Usage

1. **Open DeviceLog** — the dashboard loads immediately showing your device's live telemetry.
2. **Toggle Monitoring** — use the **ON/OFF** switch in the top-right toolbar to start or stop live data collection. The status label at the top will update to **ACTIVE** or **INACTIVE**.
3. **Read the Battery Gauge** — the large circular arc shows your current battery percentage, charge state (Charging / Discharging), and live current draw in mA.
4. **Check the Stat Cards** — the three cards below the gauge show RAM Load, Storage usage, and your device's current uptime at a glance.
5. **Review Real-Time Health** — the section below the cards shows your overall health rating, temperature in °C, and a plain-English status message.
6. **Submit a Bug** — found something wrong? Tap the **SUBMIT A BUG** button at the bottom to report it directly from the app.

---

## 🔐 Permissions

DeviceLog requests only the permissions it needs to function:

| Permission | Purpose |
|---|---|
| `BATTERY_STATS` | Read battery percentage, charge state, and current draw (mA) |
| `READ_EXTERNAL_STORAGE` | Read storage usage stats |
| `WRITE_EXTERNAL_STORAGE` | Export performance logs (Android 9 and below) |
| `PACKAGE_USAGE_STATS` | Read RAM and background app usage |
| `RECEIVE_BOOT_COMPLETED` | Restart the monitoring service after device reboot |
| `POST_NOTIFICATIONS` | Send threshold alerts to the user |

> DeviceLog does **not** collect, share, or transmit any personal data. All telemetry stays on your device.

---

## 🤝 Contributing

Contributions are welcome! If you have ideas, bug reports, or want to add a feature:

1. Fork the repository
2. Create a new branch: `git checkout -b feature/your-feature-name`
3. Commit your changes: `git commit -m "Add: your feature description"`
4. Push to your fork: `git push origin feature/your-feature-name`
5. Open a **Pull Request** with a clear description of what you changed and why

Please keep code style consistent with the existing Java codebase and test on a real device when possible.

---

## 🗺️ Roadmap

- [x] Live battery gauge with percentage, state & current draw (mA)
- [x] RAM Load, Storage, and Uptime stat cards
- [x] Real-time health status with temperature readout
- [x] Monitoring ON/OFF toggle
- [x] Dark theme UI with cyan/teal accent colors
- [x] Device telemetry footer (model identification)
- [x] In-app bug submission
- [ ] Hamburger menu navigation (additional screens)
- [ ] Notification alerts for critical battery / storage / RAM thresholds
- [ ] Historical log viewer with performance graphs
- [ ] Home screen widget showing key stats
- [ ] Scheduled auto-optimization
- [ ] Material You / dynamic color theming support

---

## 📄 License

```
MIT License

Copyright (c) 2025 Brayden Kovarsky-Steingold

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

Made with ☕ and Java by **Brayden Kovarsky-Steingold**

</div>
