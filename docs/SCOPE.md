# Vitals: Device Performance & Battery Monitor — Scope

Target device: Samsung Galaxy S25 (SM-S931x), One UI 7+ / Android 15+.
Principle: a fully offline, system-level utility. No network permissions, no analytics, no ads, no cloud backup.

## 1. What Android actually allows (no root)

This table drives the whole scope. Promising anything in the red column would mean shipping fake numbers.

| Capability | Available? | API / source | Notes |
|---|---|---|---|
| Battery level, status, plug type, voltage, temperature | Yes | `ACTION_BATTERY_CHANGED` sticky intent | Free, no permission |
| Charge counter (remaining charge) | Yes | `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER` | Documented as µAh; some OEMs report mAh. Normalised in `core` |
| Instantaneous current | Yes | `BATTERY_PROPERTY_CURRENT_NOW` | Unit and sign vary by OEM. Learned per device in `core` |
| Charge cycle count | Yes, Android 14+ | `BatteryManager.EXTRA_CYCLE_COUNT` | Verify on the S25 during M1 |
| OEM "battery health %" / state of health | No | `BATTERY_PROPERTY_STATE_OF_HEALTH` is system-only | We estimate it instead (section 3) |
| Design capacity | No public API | Device profile table + user override | S25 = 4,000 mAh, S25+ = 4,900, S25 Ultra = 5,000 |
| Thermal status (throttling level) | Yes | `PowerManager.getCurrentThermalStatus()` | 7 levels, NONE to SHUTDOWN |
| Thermal headroom forecast | Yes | `PowerManager.getThermalHeadroom()` | Rate-limited to about 1 Hz |
| Raw CPU / skin / modem temperatures | No (on Samsung) | `/sys/class/thermal` | SELinux-blocked. Battery temp is the reliable proxy |
| System RAM (free, total, low-memory flag) | Yes | `ActivityManager.getMemoryInfo()` | |
| Per-app RAM of other apps | No | Restricted since Android 10 | Out of scope |
| Device-wide CPU % | No | `/proc/stat` blocked since Android 8 | Out of scope |
| Per-core CPU frequency | Usually | `/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq` | Verify on S25. UI hides the card if blocked |
| Per-app screen time and launches | Yes, with Usage Access | `UsageStatsManager.queryEvents()` | User grants once in Settings |
| Per-app battery drain | No | `BatteryStats` needs system privileges | Explained honestly in the UI |
| Per-app mobile/Wi-Fi data | Yes, with Usage Access | `NetworkStatsManager` | Local stats only, no network permission needed. Later milestone |
| Home screen widgets | Yes | Jetpack Glance | Material You, resizable, per-instance config |

## 2. Feature scope

### MVP (in this scaffold)
- Overview: live battery %, current (mA), voltage, battery temperature, thermal status and headroom, RAM, per-core CPU frequency. Polls every 2 s while open.
- Battery: estimated health %, capacity history sparkline, degradation trend (% per month), cycle count, 48 h level chart.
- Apps: today's per-app screen time and opens, with a Usage Access onboarding gate.
- Widgets: one resizable widget with four layouts (Overview, Battery, Temperature, Memory) chosen per instance; reconfigurable by long-press.
- Settings: design capacity override, Samsung background-limit guidance, CSV export via the system file picker.
- Background sampling every 15 minutes (WorkManager minimum), 30-day raw retention, daily capacity estimates kept forever.

### v1.0 (next)
- Charge session screen: start/end level, mAh added, average/peak current, peak temperature, charger type. Detect fast vs super-fast charging.
- Usage history: 7/30-day per-app trends, category grouping, "most opened".
- Thermal history: time spent throttled per day, correlated with the foreground app.
- Alerts (local notifications only): charge limit reached (e.g. 80/85%), battery over 40 °C, sustained throttling.
- Optional foreground "session" mode (gaming/benchmark) sampling every 1 to 5 s with an ongoing notification.
- Widget expansion: 1x1 battery ring, 4x2 dashboard with sparkline, lock-screen/glanceable variants.
- Per-app data usage from `NetworkStatsManager`.
- Room migrations and exported schema committed; JSON backup/restore to a user-chosen file.

### Later
- Wear OS tile for Galaxy Watch.
- Downsampling of system samples into hourly/daily aggregates (`core/retention/Downsampler` already written).
- Shizuku integration (opt-in) to unlock deeper stats without root.
- Tablet / foldable layouts.

### Explicitly out of scope
Per-app battery %, per-app RAM, device CPU %, raw SoC temperatures. These need root or system signing.

## 3. Battery degradation method

1. Fuel-gauge estimate on every sample at ≥15%: `charge_counter / level × 100`.
2. Charge-delta estimate per continuous charging run with ≥20% gained: `Δcharge / Δlevel × 100`.
3. Median of all estimates over a rolling 7 days becomes the day's capacity; stored permanently.
4. Health % = capacity / design capacity. Trend = least-squares slope across daily estimates once ≥14 days exist.
5. Unit and sign of current/charge counter are inferred from physically plausible ranges, so Samsung quirks do not corrupt history. Raw values are always stored so the maths can be re-run if the heuristics improve.

Confidence: estimates are noisy in week one and stabilise after several charges. The UI says "Learning…" until data exists. Expect ±3 to 5% versus Samsung's own diagnostic.

## 4. Architecture

```
:core  (pure Kotlin/JVM, no Android)   battery maths, unit normalisation, usage time, thermal mapping, retention
:app   (Android)
  sensors/   BatteryReader, MemoryReader, ThermalReader, CpuFreqReader, UsageReader  (thin wrappers on platform APIs)
  data/      Room DB (4 tables), DataStore settings, MetricsRepository
  work/      SamplingWorker (15-min periodic, refreshes widgets)
  ui/        Jetpack Compose + Material 3 dynamic colour, Navigation, 4 tabs
  widget/    Glance widget + per-instance config activity
```

Stack: Kotlin 2.1, AGP 8.7, compileSdk/targetSdk 35, minSdk 31, Compose BOM 2024.12, Room 2.6 (KSP), WorkManager 2.10, Glance 1.1, DataStore 1.1. Manual DI (`AppContainer`); Hilt can be added when the graph grows.

Why the split: all logic that decides numbers shown to the user lives in `:core` and is unit-tested on the JVM in seconds. Android classes only collect raw readings.

### Data model
| Table | Key | Retention |
|---|---|---|
| `battery_sample` | auto id, indexed by time | 30 days raw |
| `system_sample` (RAM, thermal, CPU freq) | auto id, indexed by time | 30 days raw |
| `app_usage_day` | (day, package) | Forever (small) |
| `capacity_estimate` | day | Forever (the degradation history) |

Storage estimate: ~96 samples/day × 2 tables × 30 days ≈ 6k rows, well under 2 MB.

## 5. Privacy and offline guarantee
- Manifest strips `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`, so no library can add them.
- CI dumps permissions from the built debug and release APKs with `aapt2` and fails if any network permission appears.
- `allowBackup=false` and data-extraction rules exclude everything from cloud backup and device transfer.
- App labels come from a `<queries>` launcher intent, avoiding `QUERY_ALL_PACKAGES`.
- Export is user-initiated via the Storage Access Framework only.

## 6. Samsung S25 specifics
- One UI aggressively sleeps background apps. Onboarding must get the user to set Battery to **Unrestricted** and keep Vitals out of Sleeping/Deep sleeping apps, or sampling gaps appear.
- Samsung's own "Protect battery" (charge to 80%) caps level at 80%; charge-delta estimates still work because they use the gained charge, not the end level.
- Verify on device in M1: cycle count reporting, current unit/sign, cpufreq readability, thermal headroom support.

## 7. Quality bar
- `:core` unit tests (15 today) for every formula; add a test with each new metric.
- CI on every push: core tests, app unit tests, Android Lint, debug + minified release build, offline permission check, APK artifacts uploaded.
- Dependabot weekly, toolchain bumps grouped.
- Next: Compose UI tests for the permission gate, Room migration tests, a Macrobenchmark for cold start, and Baseline Profiles.
- Battery cost target: the app itself should use under 1% per day. Measure with Samsung's battery usage screen in M1.

## 8. Milestones

| Milestone | Outcome | Exit criteria |
|---|---|---|
| M0 Foundation (this commit) | Project, CI, core maths, MVP screens, widget | CI green, APK artifact installs |
| M1 Device validation | Run on the S25 for 7 days | Sensor capability matrix confirmed, sampling gaps under 10%, own battery use under 1%/day |
| M2 Battery depth | Charge sessions, alerts, capacity confidence band | Health within ±5% of Samsung Members diagnostic |
| M3 Usage and thermal history | 30-day trends, throttle time, per-app data | Charts readable on cover and main widths |
| M4 Widgets and polish | Widget family, onboarding flow, icon, accessibility pass | TalkBack and font-scale 200% checks pass |
| M5 Release | Signed release, Play listing or sideload | Real keystore, privacy policy page, Data safety form ("no data collected") |

## 9. Risks
| Risk | Mitigation |
|---|---|
| One UI kills background work | Onboarding guidance, optional foreground session mode, gap detection shown in UI |
| OEM unit/sign quirks | Raw storage + learned normalisation in `:core` |
| Capacity estimate noise | Median over 7 days, two independent methods, "Learning" state |
| Play policy on usage access | Clear in-app rationale; core features work without it |
| Toolchain drift | Version catalog + grouped Dependabot |
