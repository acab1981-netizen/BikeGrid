# Bike_ — Release Preparation Report

Date: 2026-11-21  
Project: `C:\Users\User\Desktop\Bike_` (`:app`, Android, Kotlin, Jetpack Compose, Room, osmdroid, BLE GATT)

## 1. Изоляция и очистка тестового кода

**Статус: выполнено.**

- Добавлен build flag `BuildConfig.ENABLE_TEST_TOOLS` (`app/build.gradle.kts`):
  - `debug`: `true`
  - `release`: `false`
- Симулятор датчиков / debug UI скрыт в release:
  - `ui/bikecomputer/BikeComputerScreen.kt`: кнопка BugReport и `SimulatorDialog` показываются только при `BuildConfig.ENABLE_TEST_TOOLS`.
  - `ui/tracking/TrackingViewModel.kt`: методы симулятора и `generateTestHistory30Days()` no-op в release.
  - `service/WorkoutTrackingService.kt`: `applySimulatorTick()` и simulator-enabled GPS bypass выполняются только при `BuildConfig.ENABLE_TEST_TOOLS`.
- Проверены hot-path tracking/BLE файлы на `console.log`/`println`/`print`: не обнаружено в `WorkoutTrackingService`, `BleSensorsManager`, `UnifiedSensorsManager`, `FtmsParser`.
- `BikeApplication.kt`: osmdroid config logs обёрнуты в `BuildConfig.DEBUG`.

**Примечание:** класс `SimulatorStateHolder` и код симулятора остаются в исходниках, но в release недостижимы из UI/VM/service; для полной физической вырезки можно вынести в `debug` source set.

## 2. Фоновый режим, экран и батарея

**Статус: проверено / уже реализовано; release-конфиг подтверждён.**

- **Keep Screen On / WakeLock:**
  - Активная тренировка держит экран: `KeepScreenOnEffect(enabled = metrics.isTracking)` в `BikeComputerScreen.kt` ставит/снимает `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`.
  - Сервис держит partial wake lock во время записи: `WorkoutTrackingService.acquireWakeLock()` (`PowerManager.PARTIAL_WAKE_LOCK`, `Bike_:WorkoutTracking`). WakeLock освобождается в `releaseWakeLock()`, вызываемом при stop/reset/finalize.
  - На паузе GPS polling переводится в battery-optimized режим (`GPS_UPDATE_INTERVAL_PAUSED_MS`), запись/сервис продолжаются; экранный WakeLock привязан к `isTracking`.
- **Фоновая работа:**
  - `AndroidManifest.xml`: FGS `WorkoutTrackingService` с `foregroundServiceType="location|connectedDevice"`.
  - Разрешения: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, Bluetooth/location permissions.
  - Уведомление foreground создаётся через `NotificationCompat` (`CHANNEL_ID=bike_tracking_channel`, low importance) и обновляется при старте/тике/пауза/резюм/стоп.
  - BLE/датчики работают через GATT в сервисном/менеджерном слое; автопереподключение реализовано в `BleSensorsManager` (`scheduleReconnectIfNeeded`, `autoReconnect`, reconnect attempts).

**Рекомендация для реального устройства:** проверить OEM battery restrictions (Xiaomi/Samsung/Huawei), т.к. системные агрессивные политики могут убивать FGS несмотря на корректный manifest.

## 3. Разрешения и тексты для модерации

**Статус: проверено, тексты уточнены.**

### Манифест
- Геолокация: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`.
- Bluetooth: legacy `BLUETOOTH/BLUETOOTH_ADMIN` до API 30; API 31+ `BLUETOOTH_SCAN` (`neverForLocation`) + `BLUETOOTH_CONNECT`.
- Уведомления/FGS: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*`.
- Файлы: legacy `WRITE_EXTERNAL_STORAGE` maxSdk 28; импорт/экспорт GPX/FIT/ZWO через SAF/content resolver/FileProvider/Downloads (scoped-storage friendly на современных Android).

### Пользовательские тексты (обновлены EN/RU)
- `perm_location_rationale`: «Для записи GPS-маршрута, скорости и навигации по треку во время велотренировки».
- `perm_bt_rationale`: «Для подключения датчиков пульса, каденса, мощемеров и умных велостанков по Bluetooth (BLE)».
- `perm_bg_location_rationale`: поясняет запись при выключенном экране/в фоне во время тренировки.
- `route_import_failed`: понятная ошибка для неверного/неподдерживаемого GPX/FIT.
- `sw_import_failed`: понятная ошибка для неверного `.ZWO`.

`PermissionsOnboardingScreen` запрашивает разрешения по шагам, показывает rationale, статус granted/denied, путь в настройки, отдельную обработку background location и activity recognition.

## 4. Защита от крашей и полировка UI

**Статус: выполнено (целевая hardening).**

- **Парсеры GPX/ZWO/route files:**
  - `GpxParser.parse` обёрнут в `try/catch`, выбрасывает `InvalidGpxException` с понятным сообщением; пустой GPX без точек считается невалидным.
  - `ZwoParser.parse` выбрасывает `InvalidZwoException`; пустой workout/ошибка XML дают понятный форматный error.
  - `RouteFileParser.parse` выбрасывает `InvalidRouteFileException`, поддерживает FIT/GPX fallback и не возвращает тихо пустой результат при невалидном файле.
  - UI обработка: `BikeComputerScreen`, `RoutesScreen`, `TrackingViewModel` ловят ошибки и показывают/возвращают безопасный результат; пользователь получает toast “invalid file format”.
- **Обрыв BLE:**
  - `BleSensorsManager` имеет состояние disconnect/reconnect, `scheduleReconnectIfNeeded`, `autoReconnect`, userInitiatedDisconnect guard.
  - UI/metrics уже отображают `--` при отсутствии значений (`metricTileFor` использует `?: "--"`).
  - Потеря сигнала не должна крашить приложение: GATT callbacks обрабатываются в менеджере, реконнект планируется при `autoReconnect`.
- **Сборка/линт/компиляция:**
  - `.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.
  - `.\gradlew.bat :app:testDebugUnitTest` — BUILD SUCCESSFUL (12 tests).
  - `.\gradlew.bat :app:compileReleaseKotlin` — BUILD SUCCESSFUL.
  - `.\gradlew.bat :app:lintDebug` — BUILD SUCCESSFUL.

## Релизная конфигурация

`app/build.gradle.kts`:
- `debug`: `isMinifyEnabled=false`, `ENABLE_TEST_TOOLS=true`.
- `release`: `isMinifyEnabled=true`, `isShrinkResources=true`, `ENABLE_TEST_TOOLS=false`, proguard optimize + `proguard-rules.pro`.
- `buildFeatures.buildConfig=true`.
- UTF-8 JavaCompile, Kotlin JVM 11, Compose enabled.

Рекомендуемые команды:
```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
# or for Play/internal testing bundle:
.\gradlew.bat :app:bundleRelease
```

## Полное описание функций (для релиза / магазина)

### Трекинг и велокомпьютер
- Запись вело/бег/ходьба тренировок с GPS: скорость, средняя/макс, дистанция, время, время в движении, набор высоты.
- Auto-pause `<2 km/h`, resume `>=2.5 km/h`, переключатель сохраняется.
- Ручной/авто-lap, подтверждение остановки, восстановление незавершённой тренировки.
- Экран велокомпьютера: настраиваемые плитки метрик, компактный режим, карта/метрики, keep-screen-on во время активной записи.
- Foreground service + уведомление для надёжной фоновой записи.

### Карта и маршруты
- osmdroid карта, offline tile cache, переключение источников/сеть.
- Импорт GPX/FIT маршрутов, оверлей маршрута, навигация, off-route alert > 40 м, направление/расстояние до возврата.
- Мини-профиль высоты и остаток до финиша.

### BLE датчики и тренажёр
- BLE scan/connect: HR, CSC cadence/speed, power, battery, Fitness Machine (FTMS).
- Автопереподключение известных датчиков; GPS primary, BLE fallback при потере свежего GPS.
- FTMS ERG/Resistance: target power, start/resume, quick adjust, статус контроля.

### Структурированные тренировки
- Библиотека тренировок, builder, импорт `.ZWO`, плеер шагов, target power/HR zone.
- Переходы шагов создают lap, сигналы 3-2-1, runtime restore.

### Аналитика и история
- Зоны HR/power, NP/IF/TSS, calories, время в зонах, PMC (CTL/ATL/TSB).
- История тренировок, детальный экран в Garmin-like стиле: hero KPI, zone strip, highlights, sparklines, charts, laps.
- Экспорт GPX/TCX/FIT в `Downloads/Bike_` + share; GPX содержит `trkpt/ele/time` и расширения HR/cad/power.

### Разрешения и приватность
- Пошаговый onboarding разрешений с понятными rationale.
- Геолокация/фон для записи маршрута; Bluetooth для датчиков/тренажёра; уведомления для FGS; activity recognition для шагов.

### Надёжность
- Try/catch и понятные ошибки для битых GPX/ZWO/FIT route imports.
- BLE reconnect и безопасное отображение отсутствующих значений.
- Release build excludes debug simulator/test-history tools.
