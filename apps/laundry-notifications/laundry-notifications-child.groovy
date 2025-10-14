/**
 * ============================  Laundry Notifications (Child App) ============================
 *
 *  DESCRIPTION:
 *  Monitors a vibration sensor attached to a laundry appliance and notifies the correct
 *  person (based on a hub variable value) when the appliance has been inactive for a
 *  configured period of time. Once a cycle finishes, the notification recipient hub
 *  variable is reset to the string value 'null' after 30 minutes unless the machine
 *  begins running again, in which case the reset timer is cancelled and restarted when
 *  the next cycle completes.
 *
 *  SETUP NOTES:
 *  - Create a hub variable (type: String) that will hold the name of the person to notify.
 *  - Configure mappings between hub variable values (names) and notification devices.
 *  - Choose the vibration/acceleration sensor that indicates the machine is running.
 *  - Define how many minutes of inactivity indicate that the cycle is finished.
 *
 *  Copyright 2024
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License
 *  is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *  Last modified: 2024-04-04
 */

definition(
        name: "Laundry Notifications (Child App)",
        namespace: "evcallia",
        author: "Evan Callia",
        parent: "evcallia:Laundry Notifications",
        description: "Notify the correct person when laundry finishes based on hub variable values.",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: "",
)

preferences {
    page(name: 'configurationPage', title: 'Laundry Notifications - Child', install: true, uninstall: true)
}

Map configurationPage() {
    dynamicPage(name: 'configurationPage') {
        section('Instance Name') {
            label title: 'Name this laundry monitor',
                    required: false,
                    defaultValue: app?.label
        }

        section('Instructions') {
            paragraph 'Select the vibration sensor for your machine and specify how long it must remain inactive before a cycle is considered complete.'
            paragraph 'Choose the hub variable (type: String) that stores the person to notify. Map each possible hub variable value to the appropriate notification device.'
        }

        section('Laundry Sensor Configuration') {
            input 'vibrationSensor', 'capability.accelerationSensor', title: 'Laundry vibration/acceleration sensor', required: true
            input 'inactivityMinutes', 'number', title: 'Minutes of inactivity before notifying', required: true, defaultValue: 5
            input 'cycleStatusSwitch', 'capability.switch', title: 'Optional switch to reflect cycle status', required: false
        }

        section('Recipient Hub Variable') {
            Map<String, String> variableOptions = stringHubVariableOptions()
            input 'hubVariableName', 'enum', title: 'Hub variable name (String)', required: true,
                    options: variableOptions, submitOnChange: true, offerAll: false
            if (!variableOptions) {
                paragraph 'No string hub variables were found. Create a string hub variable in Hub Variables and return to select it.'
            }
        }

        section('Notification Recipients') {
            paragraph 'Select all notification devices and enter the hub variable value (name) that should trigger each device.'
            input 'notificationDevices', 'capability.notification', title: 'Notification devices', multiple: true, submitOnChange: true, required: true

            notificationDevices?.each { device ->
                input "recipientName_${device.id}", 'text', title: "Hub variable value for ${device.displayName}", required: false, submitOnChange: false
            }
        }

        section('Notification Message') {
            paragraph 'Customize the message that is sent when the laundry cycle finishes. Use %recipient% as a placeholder for the hub variable value.'
            input 'customNotificationMessage', 'text', title: 'Notification message', required: false,
                    defaultValue: 'Laundry cycle complete for %recipient%.'
        }

        section('Debug Options') {
            input 'enableDebugLogging', 'bool', title: 'Enable debug logging', defaultValue: false
        }
    }
}

void installed() {
    logInfo 'Installed child app.'
    initialize()
}

void updated() {
    logInfo 'Updated child app.'
    unsubscribe()
    unschedule()
    initialize()
}

void initialize() {
    logInfo 'Initializing child app.'
    if (!vibrationSensor) {
        logWarn 'No vibration sensor configured; app will not subscribe to events.'
        return
    }

    state.cycleActive = false
    state.resetScheduled = false
    
    removeAllInUseGlobalVar()  // remove all in use global Hub Variables
    if (hubVariableName) {
        addInUseGlobalVar(hubVariableName)
        logDebug "Registering use of hub variable '${hubVariableName}'."
    }

    subscribe(vibrationSensor, 'acceleration', handleAccelerationEvent)
    logDebug "Subscribed to acceleration events for ${vibrationSensor.displayName}."
}

void handleAccelerationEvent(evt) {
    logDebug "Acceleration event received: ${evt.value}"
    if (evt?.value == 'active') {
        startCycle()
    } else if (evt?.value == 'inactive') {
        scheduleCycleCheck()
    }
}

void startCycle() {
    logInfo 'Laundry cycle detected as running.'
    state.cycleActive = true
    cancelPendingReset()
    unschedule(confirmCycleComplete)
    updateCycleSwitch(true)
}

void scheduleCycleCheck() {
    Integer minutes = safeInteger(inactivityMinutes, 5)
    Integer delaySeconds = Math.max(minutes, 0) * 60
    logDebug "Scheduling cycle check in ${delaySeconds} second(s)."
    runIn(delaySeconds, 'confirmCycleComplete', [overwrite: true])
}

void confirmCycleComplete() {
    if (vibrationSensor?.currentValue('acceleration') == 'active') {
        logInfo 'Vibration sensor reported active again; cancelling cycle completion.'
        return
    }

    logInfo 'Laundry cycle inactivity threshold reached; preparing notification.'
    state.cycleActive = false
    notifyConfiguredRecipient()
    scheduleHubVariableReset()
    updateCycleSwitch(false)
}

void notifyConfiguredRecipient() {
    String recipientName = currentHubVariableValue()
    if (!recipientName || recipientName?.trim()?.equalsIgnoreCase('null')) {
        logWarn 'Hub variable is empty or set to \"null\"; skipping notification.'
        return
    }

    def device = recipientDeviceMap()[recipientName]
    if (!device) {
        logWarn "No notification device mapping found for hub variable value '${recipientName}'."
        return
    }

    String message = resolveNotificationMessage(recipientName)
    logInfo "Sending notification to ${device.displayName}: ${message}"
    device.deviceNotification(message)
}

void scheduleHubVariableReset() {
    logDebug 'Scheduling hub variable reset in 30 minutes.'
    runIn(30 * 60, 'resetHubVariable', [overwrite: true])
    state.resetScheduled = true
}

void cancelPendingReset() {
    if (state.resetScheduled) {
        logInfo 'Cancelling pending hub variable reset due to new cycle activity.'
        unschedule(resetHubVariable)
        state.resetScheduled = false
    }
}

void resetHubVariable() {
    if (!hubVariableName) {
        logWarn 'Hub variable name not configured; nothing to reset.'
        return
    }

    try {
        logInfo "Resetting hub variable '${hubVariableName}' to 'null'."
        setGlobalVar(hubVariableName, 'null')
    } catch (Exception ex) {
        log.error "Failed to reset hub variable '${hubVariableName}': ${ex.message}", ex
    } finally {
        state.resetScheduled = false
        updateCycleSwitch(false)
    }
}

// Build the notification text using the configured template and recipient value.
String resolveNotificationMessage(String recipientName) {
    String template = customNotificationMessage ?: 'Laundry cycle complete for %recipient%.'
    String safeRecipient = recipientName ?: ''
    return template.replace('%recipient%', safeRecipient)
}

// Toggle the optional switch so dashboards/automations can reflect machine status.
void updateCycleSwitch(Boolean turnOn) {
    if (!cycleStatusSwitch) {
        return
    }

    try {
        if (turnOn) {
            logDebug "Turning on cycle status switch ${cycleStatusSwitch.displayName}."
            cycleStatusSwitch.on()
        } else {
            logDebug "Turning off cycle status switch ${cycleStatusSwitch.displayName}."
            cycleStatusSwitch.off()
        }
    } catch (Exception ex) {
        logWarn "Unable to update cycle status switch: ${ex.message}"
    }
}

String currentHubVariableValue() {
    if (!hubVariableName) {
        logWarn 'Hub variable name not configured.'
        return null
    }

    def val = getGlobalVar(hubVariableName)
    if (val != null) {
        return val.value
    }
    return null
}

Map recipientDeviceMap() {
    Map<String, Object> mapping = [:]
    notificationDevices?.each { device ->
        String key = settings["recipientName_${device.id}"]?.trim()
        if (key) {
            mapping[key] = device
        }
    }
    return mapping
}

// Build a map of available string hub variables so the configuration can present them in a dropdown.
Map<String, String> stringHubVariableOptions() {
    def variables = [:]

    // Create a map of variable names to their name & value concatenation
    def allVariables = getGlobalVarsByType("string")
    allVariables.each { key, value ->
        variables[key] = "$key: ${value.value}"
    }
    return variables
}

Integer safeInteger(value, Integer defaultValue) {
    try {
        return value != null ? Integer.parseInt(value.toString()) : defaultValue
    } catch (Exception ignored) {
        return defaultValue
    }
}

void logDebug(String message) {
    if (enableDebugLogging) {
        log.debug "[Laundry Notifications] ${message}"
    }
}

void logInfo(String message) {
    log.info "[Laundry Notifications] ${message}"
}

void logWarn(String message) {
    log.warn "[Laundry Notifications] ${message}"
}
