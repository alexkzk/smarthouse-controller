import os
import subprocess
import re

base_pkg = "com.alexsoft.smarthouse"
base_dir = "src/main/java/com/alexsoft/smarthouse"

moves = {
    # APPLIANCE
    "entity/Appliance.java": "appliance/Appliance.java",
    "entity/ApplianceGroup.java": "appliance/ApplianceGroup.java",
    "service/ApplianceService.java": "appliance/ApplianceService.java",
    "service/ApplianceFacade.java": "appliance/ApplianceFacade.java",
    "controller/ApplianceController.java": "appliance/ApplianceController.java",
    "repository/ApplianceRepository.java": "appliance/ApplianceRepository.java",
    "repository/ApplianceGroupRepository.java": "appliance/ApplianceGroupRepository.java",
    "enums/ApplianceState.java": "appliance/ApplianceState.java",
    "model/ApplianceSwitchEvent.java": "appliance/ApplianceSwitchEvent.java",
    "entity/ApartmentDetails.java": "appliance/ApartmentDetails.java",
    "service/ApartmentDetailsService.java": "appliance/ApartmentDetailsService.java",
    "repository/ApartmentDetailsRepository.java": "appliance/ApartmentDetailsRepository.java",
    "controller/ApartmentController.java": "appliance/ApartmentController.java",
    "service/ApartmentAvailabilityService.java": "appliance/ApartmentAvailabilityService.java",

    # MQTT
    "service/MessageSenderService.java": "mqtt/MessageSenderService.java",
    "service/MessageReceiverService.java": "mqtt/MessageReceiverService.java",
    "configuration/Mqtt.java": "mqtt/Mqtt.java",

    # ENVIRONMENT
    "service/MetarService.java": "environment/MetarService.java",
    "controller/IndicationsController.java": "environment/IndicationsController.java",
    "entity/AirspaceActivity.java": "environment/AirspaceActivity.java",
    "entity/Bme680Meta.java": "environment/Bme680Meta.java",
    "entity/IndicationV3.java": "environment/IndicationV3.java",
    "repository/AirspaceActivityRepository.java": "environment/AirspaceActivityRepository.java",
    "repository/IndicationRepositoryV3.java": "environment/IndicationRepositoryV3.java",
    "service/IndicationServiceV3.java": "environment/IndicationServiceV3.java",
    "service/AstroEventPublisher.java": "environment/AstroEventPublisher.java",
    "event/HourChangedEvent.java": "environment/HourChangedEvent.java",
    "event/SunsetEvent.java": "environment/SunsetEvent.java",
    "event/SunriseEvent.java": "environment/SunriseEvent.java",
    "repository/HourChangeTrackerRepository.java": "environment/HourChangeTrackerRepository.java",
    "configuration/MetarLocationsConfig.java": "environment/MetarLocationsConfig.java",
    "entity/Btc.java": "environment/Btc.java",
    "service/BtcService.java": "environment/BtcService.java",
    "repository/BtcRepository.java": "environment/BtcRepository.java",
    "model/metar": "environment/model/metar",
    "model/avwx": "environment/model/avwx",
    "model/flightradar24": "environment/model/flightradar24",
    "model/airplaneslive": "environment/model/airplaneslive",

    # WATCHDOG
    "entity/FcmToken.java": "watchdog/FcmToken.java",
    "service/FcmService.java": "watchdog/FcmService.java",
    "repository/FcmTokenRepository.java": "watchdog/FcmTokenRepository.java",
    "controller/FcmController.java": "watchdog/FcmController.java",
    "entity/WatchdogJob.java": "watchdog/WatchdogJob.java",
    "entity/WatchdogLog.java": "watchdog/WatchdogLog.java",
    "repository/WatchdogJobRepository.java": "watchdog/WatchdogJobRepository.java",
    "repository/WatchdogLogRepository.java": "watchdog/WatchdogLogRepository.java",
    "service/ScheduledService.java": "watchdog/ScheduledService.java",
    "watchdog": "watchdog" # Anything already in watchdog stays there (but is nested if moved) -> skip it.
}

# 1. Execute git moves
os.makedirs(f"{base_dir}/appliance", exist_ok=True)
os.makedirs(f"{base_dir}/mqtt", exist_ok=True)
os.makedirs(f"{base_dir}/environment", exist_ok=True)
os.makedirs(f"{base_dir}/watchdog", exist_ok=True)

class_moves = {}

for src, dest in moves.items():
    if src == "watchdog": continue
    src_path = os.path.join(base_dir, src)
    dest_path = os.path.join(base_dir, dest)
    if os.path.exists(src_path):
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        subprocess.run(["git", "mv", src_path, dest_path], check=True)
        
        if dest.endswith(".java"):
            class_name = os.path.basename(dest).replace(".java", "")
            old_pkg = base_pkg + "." + os.path.dirname(src).replace("/", ".")
            new_pkg = base_pkg + "." + os.path.dirname(dest).replace("/", ".")
            class_moves[class_name] = (old_pkg, new_pkg)
        else:
            for root, _, files in os.walk(dest_path):
                for file in files:
                    if file.endswith(".java"):
                        class_name = file.replace(".java", "")
                        rel_src = os.path.relpath(os.path.join(src_path, os.path.relpath(root, dest_path)), base_dir)
                        rel_dest = os.path.relpath(root, base_dir)
                        old_pkg = base_pkg + "." + rel_src.replace("/", ".")
                        new_pkg = base_pkg + "." + rel_dest.replace("/", ".")
                        class_moves[class_name] = (old_pkg, new_pkg)

# 2. Update package and imports in ALL java files
java_files = []
for root, _, files in os.walk("src"):
    for file in files:
        if file.endswith(".java"):
            java_files.append(os.path.join(root, file))

for file_path in java_files:
    with open(file_path, "r") as f:
        content = f.read()

    rel_path = os.path.relpath(file_path, base_dir)
    if "src/test/" in file_path:
        rel_path = os.path.relpath(file_path, "src/test/java/com/alexsoft/smarthouse")
    
    if not rel_path.startswith(".."):
        pkg_path = os.path.dirname(rel_path).replace("/", ".")
        expected_pkg = base_pkg + "." + pkg_path if pkg_path else base_pkg
        content = re.sub(r'^package\s+[\w\.]+;', f'package {expected_pkg};', content, flags=re.MULTILINE)

    for class_name, (old_pkg, new_pkg) in class_moves.items():
        # Match EXACTLY 'import com.alexsoft.smarthouse.old.Class;'
        old_import = f"import {old_pkg}.{class_name};"
        new_import = f"import {new_pkg}.{class_name};"
        content = content.replace(old_import, new_import)
        
        # Match static imports too
        old_static = f"import static {old_pkg}.{class_name}."
        new_static = f"import static {new_pkg}.{class_name}."
        content = content.replace(old_static, new_static)

    with open(file_path, "w") as f:
        f.write(content)
