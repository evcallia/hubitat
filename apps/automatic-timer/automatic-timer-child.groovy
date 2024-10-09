/**
 * ==========================  Automatic Timer (Child App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used as a timer to automatically turn on/off a switch after a configurable
 *  amount of time. Optionally, you may configure an override switch which prevents the timer from running.
 *  In addition to controlling the main switch, this app will also manage state of the override switch if
 *  desired.
 *
 *  Features:
 *  - Automatically turn off "main" switch after set amount of time
 *  - [Optional] Configure "override switch" that will prevent timer from running
 *  - [Optional] Set the "override switch" back to default sate when the "main" switch changes to default state (defined as
 *      the opposite of the "mainOnOffSetting")
 *  - [Optional] Set the "override switch" back to default state (defined as the opposite of the "overrideOnOffSetting")
 *      after set amount of time
 *  - [Optional] Configure a button to reset the timer for the "main" switch
 *
 *  TO INSTALL:
 *  Add code for parent app and then and child app (this). Install/create new instance of parent
 *  app and begin using.
 *
 *  Copyright 2024 Evan Callia
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  Last modified: 2024-10-09
 *
 *  Changelog:
 *
 *  1.0.0 - 2024-03-11 - Initial release
 *  1.1.0 - 2024-10-09 - Add capability for button to reset the timer for the "main" switch
 *                     - Fix logsEnabled bug where it always logged regardless of setting
 *                     - Add expandable sections for various settings groups
 *
 */

definition(
        name: "Automatic Timer (Child App)",
        namespace: "evcallia",
        author: "Evan Callia",
        parent: "evcallia:Automatic Timer",
        description: "Automatically turn switch on/off after set amount of time. Includes various configurations for " +
                "override switches which prevent timer from running.",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: ""
)

preferences {
    page name: "mainPage"
}

def mainPage() {
    dynamicPage(title: "Automatic Timer", name: "mainPage", install: true, uninstall: true) {
        section("Description") {
            paragraph "This app is designed to be used as a timer to automatically turn on/off a switch after a configurable\n" +
                    "amount of time. Optionally, you may configure an override switch which prevents the timer from running.\n" +
                    "In addition to controlling the main switch, this app will also manage state of the override switch if\n" +
                    "desired.\n" +
                    "\n" +
                    "Features:\n" +
                    "  - Automatically turn off \"main\" switch after set amount of time\n" +
                    "  - [Optional] Configure \"override switch\" that will prevent timer from running\n" +
                    "  - [Optional] Set the \"override switch\" back to default when the \"main\" switch changes to default state (defined as\n" +
                    "      the opposite of the \"mainOnOffSetting\")\n" +
                    "  - [Optional] Set the \"override switch\" back to default state (defined as the opposite of the \"overrideOnOffSetting\")\n" +
                    "      after set amount of time" +
                    "  - [Optional] Configure a button to reset the timer for the \"main\" switch"
            label description: "Name for this Automatic Timer instance", required: true
        }

        section("\"Main Switch\" Configuration") {
            input name: "mainControlDevice", type: "capability.switch", title: "Device to automatically control",
                    required: true
            input name: "mainTimeSetting", type: "number", title: "Preform action after this many minutes",
                    required: true
            input name: "mainOnOffSetting", type: "enum", title: "Turn on/off device", defaultValue: "off",
                    options: ["off", "on"]
        }

        section("\"Reset Button\" Configuration", hideable: true, hidden: false) {
            input name: "resetButtonDevice", type: "capability.pushableButton", title: "Button to reset the timer for " +
                    "the \"main\" switch", submitOnChange: true
            if (settings.resetButtonDevice != null) {
                logDebug "resetButton: ${settings.resetButtonDevice}; numberOfButtons: ${settings.resetButtonDevice.numberOfButtons}"

                def numButtons = resetButtonDevice.currentValue("numberOfButtons")
                input name: "resetButtonNumber", type: "enum", title: "Which button number", required: true,
                        options: (1..numButtons).collect { ["$it": "Button $it"] }

                input "buttonActionType", "enum", title: "Which action type", options: ["pushed", "doubleTapped"], required: true
            } else {
                app.removeSetting("resetButtonNumber")
                app.removeSetting("buttonActionType")
            }
        }

        section("\"Override Switch\" Configuration", hideable: true, hidden: false) {
            input name: "overrideSwitch", type: "capability.switch", title: "Only preform action when this switch " +
                    "is set", submitOnChange: true
            if (settings.overrideSwitch != null) {
                input name: "overrideOnOffSetting", type: "enum", title: "When above switch is on/off",
                        defaultValue: "off", options: ["off", "on"]
                input name: "setOverrideOnMainSwitchChange", type: "bool",
                        title: "Reset override switch when main switch changes to default state", defaultValue: true
                input name: "overrideTimerEnabled", type: "bool", title: "Reset override switch after set amount " +
                        "of time", defaultValue: false, submitOnChange: true
                if (settings.overrideTimerEnabled == true) {
                    input name: "overrideTimeSetting", type: "number", title: "Preform reset action after this " +
                            "many minutes", required: true
                } else {
                    app.removeSetting("overrideTimeSetting")
                }
            } else {
                app.removeSetting("overrideOnOffSetting")
                app.removeSetting("setOverrideOnMainSwitchChange")
                app.removeSetting("overrideTimerEnabled")
                app.removeSetting("overrideTimeSetting")
            }
        }

        section("Logging Configuration") {
            input name: "logsEnabled", type: "bool", title: "Enable logs", defaultValue: false
        }
    }
}

def installed() {
    log.debug "installed()"
    updated()
}

def updated() {
    log.debug "updated()"
    initialize()
}

def initialize() {
    log.debug "initialize() with setting ${settings}"

    logDebug "unsubscribe()"
    unsubscribe()

    logDebug "subscribe: mainControlDeviceHandler"
    subscribe(mainControlDevice, "switch", "mainControlDeviceHandler")

    logDebug "subscribe: overrideSwitchHandler"
    subscribe(overrideSwitch, "switch", "overrideSwitchHandler")

    logDebug "subscribe: buttonResetHandler"
    subscribe(resetButtonDevice, buttonActionType, "buttonResetHandler")
}

def uninstalled() {
    log.debug "uninstalled()"
}

def buttonResetHandler(evt) {
    logDebug "buttonResetHandler() called: ${evt.name} ${evt.value}"

    if (evt.name == buttonActionType && evt.value == resetButtonNumber) {
        checkAndSchedule(mainControlDevice.currentValue("switch"))
    }
}

def overrideSwitchHandler(evt) {
    logDebug "overrideSwitchHandler() called: ${evt.name} ${evt.value}"

    // Unschedule no matter what
    logDebug "unschedule(delayedMainSwitchHandler)"
    unschedule("delayedMainSwitchHandler")

    if (overrideTimerEnabled) {
        logDebug "unschedule(delayedOverrideSwitchHandler)"
        unschedule("delayedOverrideSwitchHandler")
    }

    if (evt.value == overrideOnOffSetting) {
        if (mainControlDevice.currentValue("switch") != mainOnOffSetting) {
            logDebug "runIn(${mainTimeSetting * 60}, delayedMainSwitchHandler)"
            runIn(mainTimeSetting * 60, "delayedMainSwitchHandler")
        }
    }

    if (evt.value != overrideOnOffSetting) {
        if (overrideTimerEnabled && overrideSwitch.currentValue("switch") != overrideOnOffSetting) {
            logDebug "runIn(${overrideTimeSetting*60}, delayedOverrideSwitchHandler)"
            runIn(overrideTimeSetting*60, delayedOverrideSwitchHandler)
        }
    }
}

def mainControlDeviceHandler(evt) {
    logDebug "mainControlDeviceHandler() called: ${evt.name} ${evt.value}"
    checkAndSchedule(evt.value)
}

def checkAndSchedule(mainControlDeviceValue) {
    logDebug "checkAndSchedule() called: mainControlDeviceValue: ${mainControlDeviceValue}"

    // Unschedule no matter what
    // 1. If device is meant to be turned off in 5 min and then turns off, we don't need to keep timing
    // 2. Shouldn't be possible to receive another on if it's already on, but if so then reset the timer
    logDebug "unschedule(delayedMainSwitchHandler)"
    unschedule("delayedMainSwitchHandler")

    if (mainControlDeviceValue != mainOnOffSetting) {
        if (overrideSwitch) {
            if (overrideSwitch.currentValue("switch") == overrideOnOffSetting) {
                logDebug "runIn(${mainTimeSetting*60}, delayedMainSwitchHandler)"
                runIn(mainTimeSetting*60, "delayedMainSwitchHandler")
            } else {
                logDebug "override is set, skipping delayedMainSwitchHandler"
            }
        } else {
            logDebug "runIn(${mainTimeSetting*60}, delayedMainSwitchHandler)"
            runIn(mainTimeSetting*60, "delayedMainSwitchHandler")
        }
    } else {
        if (overrideSwitch && setOverrideOnMainSwitchChange) {
            logDebug "setting override switch (${overrideSwitch}) based on \"main\" switch (${mainControlDevice})"
            if (overrideOnOffSetting == "on") {
                logDebug "setting ${overrideSwitch} to on"
                overrideSwitch.off()
            } else {
                logDebug "setting ${overrideSwitch} to off"
                overrideSwitch.off()
            }
        }
    }
}

def delayedMainSwitchHandler() {
    logDebug "delayedMainSwitchHandler: mainOnOffSetting: ${mainOnOffSetting}"
    if (mainOnOffSetting == "on") {
        logDebug "delayedMainSwitchHandler: mainOnOffSetting == on. Turning on."
        mainControlDevice.on()
    } else {
        logDebug "delayedMainSwitchHandler: mainOnOffSetting == off. Turning off."
        mainControlDevice.off()
    }
}

def delayedOverrideSwitchHandler() {
    logDebug "delayedOverrideSwitchHandler: overrideOnOffSetting: ${overrideOnOffSetting}"
    if (overrideOnOffSetting == "on") {
        logDebug "delayedOverrideSwitchHandler: overrideOnOffSetting == on. Turning on."
        overrideSwitch.on()
    } else {
        logDebug "delayedOverrideSwitchHandler: overrideOnOffSetting == off. Turning off."
        overrideSwitch.off()
    }
}

void logDebug(msg) {
    if (logsEnabled) {
        log.debug(msg)
    }
}
