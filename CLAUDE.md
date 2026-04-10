# Smarthouse Controller — Developer Notes

## Overview

Spring Boot 3.4.0 IoT home automation backend. Runs on a local server, controls smart appliances via MQTT/Zigbee2MQTT, reads sensor data, and applies automation rules (illuminance-based lighting, temperature/humidity control).

## Infrastructure

- **DB**: PostgreSQL at the configured PostgreSQL host, database `smarthouse`, user `smarthouse`, credentials in `~/.pgpass`
- **Schema**: `main` (all application tables live here)
- **MQTT broker**: Zigbee2MQTT at same host, topic `zigbee2mqtt/#`
- **Timezone**: Application operates in `America/New_York`. All internal timestamps stored as UTC (`LocalDateTime` without zone = UTC by convention). Use `DateUtils.getUtc()` for UTC and `DateUtils.getLocalDateTime()` for Eastern time.
- **Location**: Miami area (lat `25.76`, lon `-80.19`), used for sunrise/sunset calculations

## Key Appliances

| Code | Description | Control type | Zigbee topic |
|---|---|---|---|
| `AC` | Air Conditioner | Temperature (setting=25, hyst=0.5, min off=15m, min on=10m) | — (custom MQTT) |
| `DEH` | Dehumidifier | Absolute humidity (setting=10, hyst=0.5, min off=10m, min on=30m) | `zigbee2mqtt/lr-sp-dehumidifier/set` |
| `FAN` | Ventilation | Controlled by DEH power consumption logic | — (custom MQTT) |
| `LR-LUTV` | LR Led Under TV | Illuminance, inverted (setting=75, hyst=25) | `zigbee2mqtt/lr-led-under-tv/set` |
| `MB-LOB` | MB Led Over Bed | Illuminance, inverted (setting=75, hyst=25) | `zigbee2mqtt/mb-led-over-bed/set` |
| `MB-LOTV` | MB Led Over TV | Illuminance, inverted (setting=75, hyst=25) | `zigbee2mqtt/mb-led-over-tv/set` |
| `TER-LIGHTS` | Terrace Lights | Illuminance, inverted (setting=75, hyst=25) — also controls `zigbee2mqtt/WRKTABLE/set` | `zigbee2mqtt/TER-LIGHTS/set` |

**Appliance Group ALGHTS (id=1)**: LR-LUTV, MB-LOB, MB-LOTV, TER-LIGHTS — all illuminance-controlled lights. `turn_off_hours=22` (turn off at 10 PM Eastern).

**Reference sensor** for ALGHTS: `mb-lis-outdoor` (outdoor illuminance sensor). Lights turn ON when lux < 50, OFF when lux > 100 (inverted logic).

## Power Control Flow

`ScheduledService` runs `ApplianceService.powerControl()` every 10 seconds for all appliances.

```
powerControl()
  → read reference sensor average over averagePeriodMinutes
  → checkLock() — expire lock if lockedUntilUtc passed
  → if not locked:
      → determineScheduledSetting() — override setting from schedule if changed
      → evaluate on/off condition with hysteresis
      → ApplianceFacade.toggle(appliance, decision, utc, "pwr-control", sendMqtt=true)
```

**Inverted logic** (used by lights): ON when avg < setting - hysteresis, OFF when avg > setting + hysteresis.

**AC special case**: When AC is OFF, FAN is toggled on a minute-based schedule if dehumidifier power consumption > 500W.

## Locking System

Locks prevent automatic power control from overriding a state set by the user.

### `ApplianceFacade.setLock()` — group 1 (ALGHTS) lights

Triggered when `requester` is `http-controller`, `zigbee2mqtt/*`, or `turn off hours setting`.

| Condition | State | Action |
|---|---|---|
| `isDark()` = true | OFF | Lock until `wakeUpTime()` — only if new time is later than current lock |
| `isDark()` = true | ON | Unlock |
| `isDark()` = false | OFF | Unlock |
| `isDark()` = false | ON | Lock until `getNearestSunsetTime() + 1 hour` |

**`wakeUpTime()`**: 6:55 AM Eastern on weekdays, 8:00 AM Eastern on weekends (returned as UTC).

**`isDark()`**: Returns `true` when nearest sunrise is before nearest sunset (i.e., we haven't had sunrise yet today, or sunset already passed). Uses Eastern time consistently.

### `ApplianceFacade.setLock()` — other appliances (when switched)

- Switched to OFF + `minimumOffCycleMinutes` set → lock for that duration (rule 5)
- Switched to ON + `minimumOnCycleMinutes` set → lock for that duration (rule 6)

### `ApplianceService.checkLock()`

Called every power control cycle. Clears lock when `lockedUntilUtc` is in the past, logs `lock.expired` event.

## Event System

All events stored in `main.event` table.

### Schema

| Column | Type | Description |
|---|---|---|
| `id` | int | Auto-increment (sequence `event_sq_v3`) |
| `utc_time` | timestamp | When it happened (UTC) |
| `type` | varchar | Event type / action name |
| `device` | varchar(64) | Appliance code (e.g. `MB-LOTV`), null for system events |
| `mqtt_topic` | varchar(255) | MQTT topic when event originated from MQTT |
| `data` | text (JSON) | Additional structured data |

### Event types

| `type` | `device` | `data` keys |
|---|---|---|
| `switch` | appliance code | `state`, `source` |
| `locked-until` | appliance code | `until` (UTC datetime), `rule` (1/4/5/6) |
| `unlocked` | appliance code | `rule` (2/4) |
| `lock.preserved` | appliance code | `existing`, `attempted` |
| `lock.expired` | appliance code | `wasLockedUntil` |
| `pwr-control.trigger` | appliance code | `decision`, `avg`, `setting`, `hysteresis` — logged only on actual state change |
| `pwr-control.check` | appliance code | `decision`, `avg`, `setting`, `hysteresis` — logged when condition met but state already correct |
| `http.locked` | appliance code | `locked` (boolean) |
| `http.lockedUntil` | appliance code | `requested`, `previous`, `result` |
| `http.lockedUntil.cleared` | appliance code | — |
| `inbound.mqtt.msg` | device from topic | raw MQTT payload fields, `mqtt_topic` set |
| `group.{code}.turn-off-hours.triggered` | null | `hour`, `appliances` |
| `new.hour` | null | — |
| `sunset` | null | — |
| `application.startup` / `application.shutdown` | null | — |

### Lock rules reference

| Rule | Scenario |
|---|---|
| 1 | Dark + turned OFF via user/zigbee → lock until wake-up time |
| 2 | Dark + turned ON via user/zigbee → unlock |
| 4 | Not dark + turned OFF → unlock / turned ON → lock until sunset+1h |
| 5 | Non-group appliance switched OFF, has minimumOffCycleMinutes → lock |
| 6 | Non-group appliance switched ON, has minimumOnCycleMinutes → lock |

## MQTT Message Handling

`MessageReceiverService` subscribes to `zigbee2mqtt/#`.

- All inbound messages logged as `inbound.mqtt.msg` event with `device` and `mqtt_topic`
- Measurements (power, illuminance, temperature, humidity) saved to `indication_v3`
- State changes: appliance looked up first by code (`topic.split("/")[1]`), then falls back to `zigbee2MqttTopic` prefix match — handles cases where topic name differs from appliance code (e.g. `mb-led-over-tv` → `MB-LOTV`)
- `sendMqtt=false` on Zigbee-triggered toggles to avoid echo loops
- Requester passed as `topic` (e.g. `zigbee2mqtt/mb-led-over-tv`) so `switch` events show origin

## REST API

Base path: `/appliances`

- `GET /appliances?requesterId=X` — list all appliances, logs requester
- `PATCH /appliances/{code}` — partial update, supported fields:
  - `state` → `ON` / `OFF` — triggers toggle + post-commit power control
  - `locked` → boolean
  - `lockedUntil` → `yyyyMMdd-HHmm` string, or `"null"` to clear. If lock already exists, adds duration relative to now.
  - `setting`, `hysteresis`, `powerSetting`, `consumptionKwh`, `referenceSensors`, `description`

## Scheduled Jobs

| Schedule | Method | Description |
|---|---|---|
| Every 10s | `ScheduledService.powerControl()` | Runs power control for all appliances |
| Every 3s | `ScheduledService.calculateTrends()` | Calculates 1-min and 5-min trends for temp/humidity at MB and LR sensors |
| Every 1s | `AstroEventPublisher.detectHourChange()` | Fires `HourChangedEvent`, triggers turn-off-hours group rules |
| Every 60s | `AstroEventPublisher.detectSunset()` | Fires `SunsetEvent` when sunset occurs |

## DB Migration Scripts

Located in `src/dbscript/`. Naming: `V{timestamp}_{description}.sql`. Applied manually (not Flyway). Run against `main` schema on the configured PostgreSQL host.

## Common Queries

```sql
-- Recent events for a device
SELECT id, utc_time, type, data FROM main.event
WHERE device = 'MB-LOTV' ORDER BY utc_time DESC LIMIT 50;

-- All switch events
SELECT id, utc_time, device, data FROM main.event
WHERE type = 'switch' ORDER BY utc_time DESC LIMIT 50;

-- Current appliance state
SELECT code, description, state, locked, locked_until_utc FROM main.appliance ORDER BY code;

-- Latest illuminance reading
SELECT utc_time, value FROM main.indication_v3
WHERE location_id = 'mb-lis-outdoor' AND measurement_type = 'illuminance'
ORDER BY utc_time DESC LIMIT 1;
```
