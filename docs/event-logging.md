# Event Logging Mechanism

## Overview

All significant system actions are persisted to the `main.event` table in PostgreSQL. Events are the primary audit trail for appliance state changes, automation decisions, lock lifecycle, HTTP operations, and MQTT traffic.

---

## Database Schema

**Table**: `main.event`
**Sequence**: `event_sq_v3`
**Entity**: `com.alexsoft.smarthouse.entity.Event`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | Auto-increment via `event_sq_v3` |
| `utc_time` | TIMESTAMP | When the event occurred (UTC) |
| `type` | VARCHAR | Event type identifier |
| `device` | VARCHAR(64) | Appliance code (e.g. `MB-LOTV`, `AC`). Null for system-level events |
| `mqtt_topic` | VARCHAR(255) | Source MQTT topic — only set for Zigbee-originated events |
| `data` | TEXT (JSON) | Flexible key-value payload stored as JSON |

The `data` column maps to `Map<String, Object>` and is serialized/deserialized via a JPA converter.

---

## Event Reference

### `switch`

Logged when an appliance changes state.

**Source**: `ApplianceFacade.toggle()` — only when state actually changes
**Device**: appliance code
**MQTT topic**: set if `requester` starts with `zigbee2mqtt/`

| Data key | Description |
|---|---|
| `state` | New state: `"ON"` or `"OFF"` |
| `source` | Who triggered the switch: `"http-controller"`, `"zigbee2mqtt/TOPIC"`, `"pwr-control"`, `"turn off hours setting"` |

---

### `locked-until`

Logged when a lock is placed on an appliance.

**Source**: `ApplianceFacade.setLock()` (called from `toggle()`)
**Device**: appliance code

| Data key | Description |
|---|---|
| `until` | Lock expiration (UTC datetime string) |
| `rule` | Which lock rule fired: `1`, `4`, `5`, or `6` |

**When each rule fires:**

| Rule | Condition |
|---|---|
| 1 | Dark + user/zigbee turns appliance OFF → lock until wake-up time |
| 4 | Not dark + user/zigbee turns appliance ON → lock until sunset + 1 hour |
| 5 | Non-group appliance switched OFF, `minimumOffCycleMinutes` configured |
| 6 | Non-group appliance switched ON, `minimumOnCycleMinutes` configured |

---

### `lock.preserved`

Logged when rule 1 would set a lock, but an existing lock already extends further (user extension preserved).

**Source**: `ApplianceFacade.setLock()`
**Device**: appliance code

| Data key | Description |
|---|---|
| `existing` | Current lock expiration (UTC string) |
| `attempted` | The lock time that would have been applied |

---

### `unlocked`

Logged when a lock is cleared by a user or Zigbee action.

**Source**: `ApplianceFacade.setLock()`
**Device**: appliance code

| Data key | Description |
|---|---|
| `rule` | Which rule triggered the unlock: `2` or `4` |

**When each rule fires:**

| Rule | Condition |
|---|---|
| 2 | Dark + user/zigbee turns appliance ON → unlock |
| 4 | Not dark + user/zigbee turns appliance OFF → unlock |

---

### `lock.expired`

Logged when power control detects that a lock's expiration time has passed.

**Source**: `ApplianceService.checkLock()` (called every 10s from `powerControl()`)
**Device**: appliance code

| Data key | Description |
|---|---|
| `wasLockedUntil` | The expired lock time (UTC string) |

---

### `pwr-control.trigger`

Logged when automated power control decides to change appliance state.

**Source**: `ApplianceService.logPwrControlDecision()` — only when current state ≠ decision
**Device**: appliance code

| Data key | Description |
|---|---|
| `decision` | Target state: `"on"` or `"off"` |
| `avg` | Average sensor reading used for decision (Double) |
| `setting` | Configured threshold (Double) |
| `hysteresis` | Configured hysteresis (Double) |

---

### `pwr-control.check`

Logged when automated power control evaluates state but finds it already correct (no change needed).

**Source**: `ApplianceService.logPwrControlDecision()` — only when current state = decision
**Device**: appliance code

| Data key | Description |
|---|---|
| `decision` | Target state: `"on"` or `"off"` |
| `avg` | Average sensor reading (Double) |
| `setting` | Configured threshold (Double) |
| `hysteresis` | Configured hysteresis (Double) |

---

### `group.{groupCode}.turn-off-hours.triggered`

Logged when the scheduled hour-change check fires a group turn-off.

**Source**: `ApplianceService.onHourChanged()` (listener for `HourChangedEvent`)
**Device**: null (group event)

| Data key | Description |
|---|---|
| `hour` | The hour that triggered the rule (Integer) |
| `appliances` | List of appliance codes turned off |

Example type: `group.ALGHTS.turn-off-hours.triggered`

---

### `http.locked`

Logged when an HTTP client sets the `locked` boolean via REST API.

**Source**: `ApplianceController.partiallyUpdateAppliance()` — on PATCH with `locked` field
**Device**: appliance code

| Data key | Description |
|---|---|
| `locked` | The boolean value set |

---

### `http.lockedUntil`

Logged when an HTTP client sets a specific `lockedUntil` datetime via REST API.

**Source**: `ApplianceController.partiallyUpdateAppliance()` — on PATCH with `lockedUntil` field (not `"null"`)
**Device**: appliance code

| Data key | Description |
|---|---|
| `requested` | Raw value from request (`yyyyMMdd-HHmm`) |
| `previous` | Previously set lock time, or `"null"` |
| `result` | Final calculated lock time (relative adjustment applied if previously locked) |

---

### `http.lockedUntil.cleared`

Logged when an HTTP client clears a lock via REST API (`lockedUntil = "null"`).

**Source**: `ApplianceController.partiallyUpdateAppliance()`
**Device**: appliance code
**Data**: empty

---

### `inbound.mqtt.msg`

Logged for every MQTT message received on `zigbee2mqtt/#`.

**Source**: `MessageReceiverService.messageHandler()` (Spring Integration ServiceActivator)
**Device**: extracted from topic (`zigbee2mqtt/{device}/...`), or null
**MQTT topic**: set to the full topic string

| Data key | Description |
|---|---|
| *(all payload fields)* | Full key-value pairs from the parsed MQTT JSON payload |

---

### `application.startup`

Logged when Spring Boot finishes initialization.

**Source**: `AstroEventPublisher.readLastHour()` (listener for `ApplicationReadyEvent`)
**Device**: null
**Data**: empty

---

### `application.shutdown`

Logged when the Spring application context closes.

**Source**: `AstroEventPublisher.onApplicationEvent()` (listener for `ContextClosedEvent`)
**Device**: null
**Data**: empty

---

### `sunset`

Logged once per day when local time crosses the calculated sunset time.

**Source**: `AstroEventPublisher.detectSunset()` (scheduled every 60s)
**Device**: null
**Data**: empty

---

### `new.hour`

Logged once per hour when the local time hour changes.

**Source**: `AstroEventPublisher.detectHourChange()` (scheduled every 1s)
**Device**: null
**Data**: empty

---

## Design Notes

- **Timestamps**: All events store UTC time. Lock-related events (`locked-until`, `lock.preserved`, `unlocked`) use a fresh `DateUtils.getUtc()` call at the moment of logging — not a passed-in timestamp — so sequential events always appear in the correct order.
- **Conditional pwr-control events**: `pwr-control.trigger` fires only when state actually changes; `pwr-control.check` fires when the condition is met but state is already correct. Together they provide a full trace of automation decisions without requiring state-change inference.
- **MQTT source tracing**: Switch events originating from Zigbee carry the full topic as both `source` in data and `mqttTopic` column, enabling filtering by origin device.
- **Lock rule tagging**: Every lock/unlock event records the rule number so audit queries can distinguish automatic cycle locks (rules 5/6) from time-of-day locks (rules 1/2/4).

---

## Useful Queries

```sql
-- Recent events for a specific appliance
SELECT id, utc_time, type, data
FROM main.event
WHERE device = 'MB-LOTV'
ORDER BY utc_time DESC
LIMIT 50;

-- All switch events with source
SELECT id, utc_time, device, data->>'state' AS state, data->>'source' AS source
FROM main.event
WHERE type = 'switch'
ORDER BY utc_time DESC
LIMIT 50;

-- Lock lifecycle for an appliance
SELECT id, utc_time, type, data
FROM main.event
WHERE device = 'LR-LUTV'
  AND type IN ('locked-until', 'lock.preserved', 'unlocked', 'lock.expired')
ORDER BY utc_time DESC
LIMIT 50;

-- Power control decisions (changes only)
SELECT id, utc_time, device, data
FROM main.event
WHERE type = 'pwr-control.trigger'
ORDER BY utc_time DESC
LIMIT 50;

-- All inbound MQTT traffic for a topic
SELECT id, utc_time, mqtt_topic, data
FROM main.event
WHERE type = 'inbound.mqtt.msg'
  AND mqtt_topic LIKE 'zigbee2mqtt/mb-lis-outdoor%'
ORDER BY utc_time DESC
LIMIT 50;

-- System lifecycle events
SELECT id, utc_time, type
FROM main.event
WHERE type IN ('application.startup', 'application.shutdown', 'sunset', 'new.hour')
ORDER BY utc_time DESC
LIMIT 50;
```
