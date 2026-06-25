# Smarthouse Controller

Spring Boot Modulith IoT home automation backend. Collects data from home IoT sensors and external sources, persists it in PostgreSQL, and applies automation rules (MQTT, scheduling, alerts). Runs on a home server (i7-4770k, 192.168.0.201).

## Architecture

This project is built using the **Spring Modulith** architectural style. The application is divided into independent, decoupled domain modules:

* `core`: Shared entities and repositories.
* `appliance`: Appliance control and state tracking logic.
* `environment`: Environment indications (temperature, humidity, weather, astrodynamics).
* `mqtt`: Infrastructure for messaging; parses raw MQTT messages and publishes decoupled Spring application events.
* `watchdog`: Background scheduled tasks, alerting, and integrations like Firebase Cloud Messaging (FCM).

The architecture is strictly enforced during testing by `ModulithTest` which uses ArchUnit to prevent cyclic dependencies and unauthorized cross-module internal access. Modules interact exclusively through public APIs or loosely-coupled Spring Application Events.

## System Structure

The `smarthouse-controller` acts as the central hub of a broader home automation ecosystem:

* **ESP8266 Devices (Firmware):** Custom-built ESP8266 room sensors (temperature, humidity) and actuators (AC/Fan relay controllers) that publish data and receive commands via MQTT.
* **External Automations & Integrations:**
  * **Node-RED:** A standalone flow builder used for decoupled automation logic (e.g., Zigbee lighting controls), interacting with the MQTT broker alongside the controller.
  * **Home Assistant:** Acts as an external integration platform, capable of observing state and sending commands via the shared MQTT broker.
* **Client Applications (External Users):**
  * **Android App:** A mobile interface consuming the controller's REST API for remote management and receiving push notifications via Firebase FCM.
  * **Linux Desktop App:** A PyQt5 system tray application that monitors current state and provides quick access to controls (like AC and locking) via the REST API.

## Infrastructure

All services run in Kubernetes (k3s) in the `smarthouse` namespace.

| Service | Description | External port |
|---|---|---|
| `smarthouse-controller` | Spring Boot app | 24867 |
| `smarthouse-db` | PostgreSQL 14.5 | 24870 |
| `smarthouse-influxdb` | InfluxDB 2.0 | 8086 |
| `smarthouse-mqtt-broker` | Eclipse Mosquitto | 1883 |

## Monitoring & GitOps

| Tool | URL | Credentials |
|---|---|---|
| ArgoCD | http://192.168.0.201:30080 | admin / see k8s secret |
| Grafana | http://192.168.0.201:30300 | admin / see k8s secret |
| Prometheus | http://192.168.0.201:30090 | — |

## Kubernetes — common commands

> SSH into the server first: `ssh alexkzk@192.168.0.201`
> 
> `KUBECONFIG` is set in `~/.bashrc` — no prefix needed after login.

### Check status

```bash
# All pods across the cluster
kubectl get pods -A

# Smarthouse namespace
kubectl get pods -n smarthouse
kubectl get all -n smarthouse

# Recent events (errors, restarts)
kubectl get events -n smarthouse --sort-by='.lastTimestamp'
```

### Logs

```bash
# App logs (last 100 lines)
kubectl logs -n smarthouse -l app=smarthouse-controller --tail=100

# Follow logs live
kubectl logs -n smarthouse -l app=smarthouse-controller -f

# Logs for a specific pod
kubectl logs -n smarthouse <pod-name>
```

### Restart a service

```bash
kubectl rollout restart deployment/smarthouse-controller -n smarthouse
kubectl rollout restart deployment/smarthouse-db -n smarthouse
kubectl rollout restart deployment/smarthouse-influxdb -n smarthouse
kubectl rollout restart deployment/smarthouse-mqtt-broker -n smarthouse
```

### Describe a pod (useful for crash diagnosis)

```bash
kubectl describe pod -n smarthouse <pod-name>
```

### Scale down / up

```bash
# Stop a service (scale to 0)
kubectl scale deployment/smarthouse-controller -n smarthouse --replicas=0

# Start it back
kubectl scale deployment/smarthouse-controller -n smarthouse --replicas=1
```

## Deploying changes

Push to `master` — GitHub Actions builds a new Docker image and deploys it automatically.

ArgoCD watches the `k8s/` folder and applies any manifest changes within ~3 minutes of a push.

To force an immediate ArgoCD sync:
```bash
kubectl annotate application smarthouse-controller -n argocd argocd.argoproj.io/refresh=normal --overwrite
```

## k8s manifests

All manifests live in `k8s/`. ArgoCD syncs this folder to the `smarthouse` namespace automatically.

| File | Description |
|---|---|
| `deployment.yaml` | smarthouse-controller app |
| `postgres.yaml` | PostgreSQL + ClusterIP service |
| `influxdb.yaml` | InfluxDB + ClusterIP service |
| `mosquitto.yaml` | Mosquitto + ConfigMap + ClusterIP service |
| `configmap.yaml` | App environment variables |
| `secret.yaml` | DB password |
| `service.yaml` | smarthouse-controller ClusterIP service |
| `namespace.yaml` | smarthouse namespace |
| `argocd-app.yaml` | ArgoCD Application definition |

## Database operations

### Dump & restore

```bash
# Dump a table (compressed)
docker exec -i smarthouse-db pg_dump -U smarthouse -d smarthouse --table="main.indication_v3" --data-only | gzip > ~/indication_v3.sql.gz

# Restore
gunzip -c ~/Downloads/indication_v3.sql.gz | psql -U smarthouse -d smarthouse -h localhost -p 24870

# Reset sequence after restore
SELECT setval('main.indication_sq_v3', <last_id>);
```

### InfluxDB sync

```bash
# Full resync from indication_v3
influx delete --bucket smarthouse-bucket --org Home --start 1970-01-01T00:00:00Z --stop 2100-01-01T00:00:00Z
curl -X POST '192.168.0.201:24867/smarthouse/indications/influx-sync'

# Partial resync (date range)
curl -X POST '192.168.0.201:24867/smarthouse/indications/influx-sync?startDate=2025-10-17T00%3A00%3A00&endDate=2025-10-27T00%3A00%3A00'
```
