/**
 * ==========================  Schedule Manager (Child App) ==========================
 *
 *  DESCRIPTION:
 *  This app allows users to configure a time table, per device, and schedule the desired state for each configured
 *  time. Users can select any number of switches/dimmers, schedule them based on a set time or sunrise/set (with
 *  offset), and configure the desired state for that time. Additionally, users can pause the schedule for individual
 *  times. Advanced options include only running for desired modes or when a specific switch is set in addition to
 *  the ability to manually pause all schedules.
 *
 *  Features:
 *  - Schedule any number of switches/dimmers
 *  - Schedules based on selected time or sunrise/set with offset
 *  - Individual schedules may be paused
 *  - Set desired state for switch/dimmer to be in at specified time
 *  - [Optional] Configure which modes to run schedules for
 *  - [Optional] Configure "override switch" that will prevent schedules from running
 *  - [Optional] Option to pause all schedules
 *
 *  TO INSTALL:
 *  Add code for parent app and then and child app (this). Install/create new instance of parent
 *  app and begin using. This app may also be installed via Hubitat Package Manager (preferred).
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
 *  NOTICE: This file has been modified by *Evan Callia* under compliance with
 *  the Apache 2.0 License from the original work of *Switch Scheduler and More*.
 *
 * Below link is for original source (@kampto)
 * https://github.com/kampto/Hubitat/blob/main/Apps/Switch%20Scheduler%20and%20More
 *
 * =======================================================================================
 *
 *  Last modified: 2024-11-25
 *
 *  Changelog:
 *
 *  1.0.0 - 2024-11-18 - Initial release
 *  1.0.1 - 2024-11-25 - Fix typo when logging
 *                     - Fix sunrise/set offset not accounted for
 *                     - Fix issue where sunrise/set wasn't updated daily
 *  1.1.0 - 2024-12-31 - BREAKING CHANGE - App name will be reset
 *                     - Show "(paused)" in app name when app is paused
 *                     - Additional error handling for default schedules and when new devices are added to a schedule
 *  2.0.0 - 2025-04-01 - BREAKING CHANGE - Sunrise/set times will not update unless app is re-saved. It will still run, just using the sunrise/set values at the time the app was updated.
 *                     - Additional error handling for default schedules and when new devices are added to a schedule
 *                     - Support for Hub Variables (datetime) used as start time
 *                       - This allows users to select a Hub Variable to be used as the start time for schedules
 *                       - Can use datetime, time or date (at midnight)
 *                       - Supports variable renaming
 *                       - Registers the use of a Hub Variable with the Hub
 *                       - Update schedules when the Hub Variable changes
 *                     - Fix null pointer that happened occasionally when a new device was added
 *  2.0.1 - 2025-04-03 - Fix app version not showing properly
 *
 */

def titleVersion() {
    state.name = "Schedule Manager"
    state.version = "2.0.1"
}

definition(
        name: "Schedule Manager (Child App)",
        label: "Schedule Manager Instance",
        namespace: "evcallia",
        author: "Evan Callia",
        description: "Child app for schedule manager",
        category: "Control",
        parent: "evcallia:Schedule Manager",
        iconUrl: "",
        iconX2Url: ""
)
preferences { page(name: "mainPage") }


//****  Main Page Inputs/Set-Up ****//

def mainPage() {
    if (app.getInstallationState() != "COMPLETE") {
        hide = false
    } else {
        hide = true
    }

    if (state.devices == null) state.devices = [:]

    if (logEnableBool) { logDebug "Main Page Refresh. Devices: ${devices}" }

    dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
        displayTitle()

        section(getFormat("header", "Initial Set-Up"), hideable: true, hidden: hide) {
            input name: "appName", type: "string", title: "<b>Name this App</b>", required: true, submitOnChange: true, width: 3
            input "devices", "capability.switch, capability.SwitchLevel", title: "<b>Select Devices schedule</b>", required: true, multiple: true, submitOnChange: true, width: 6

            devices.each { dev ->
                if (!state.devices["$dev.id"]) {
                    state.devices["$dev.id"] = [
                        zone: 0,
                        capability: "Switch",
                        schedules: [
                            (UUID.randomUUID().toString()): generateDefaultSchedule()
                        ],
                    ]
                }
            }

            if (appName) {
                String label = appName
                if (pauseBool) {
                    label += " <span style='color:red'>(Paused)</span>"
                }
                app.updateLabel(label)
                updated()
            }
        }

        section {
            if (devices) {
                def devicesToRemove = []
                state.devices.each { deviceId, deviceConfig ->
                    // If the device ID is not in the currently selected devices, mark it for removal
                    if (!devices.find { it.id == deviceId }) {
                        devicesToRemove << deviceId
                    }
                }

                // Remove the devices from state.devices
                devicesToRemove.each { deviceId ->
                    state.devices.remove(deviceId)
                }

                paragraph displayTable()


                //**** Create hidden inputs used to modify values in the table ****//

                // Input Start Time
                if (state.newStartTime) {
                    input name: "newStartTime", type: "time", title: getFormat("noticable", "<b>Enter Start/On Time, Applies to all checked days for Switch.<br><small>Uses 24hr time, &nbsp Hit Update</small>"), required: false, submitOnChange: true, width: 5, newLineAfter: true, style: 'margin-left:10px'
                    if (newStartTime) {
                        def (deviceId, scheduleId) = state.newStartTime.tokenize('|')
                        state.devices[deviceId].schedules[scheduleId].startTime = newStartTime
                        state.remove("newStartTime")
                        app.removeSetting("newStartTime")
                        paragraph "<script>{changeSubmit(this)}</script>"
                    }
                }

                // Sunrise/Sunset or variable offset time
                if (state.newOffsetTime) {
                    input name: "newOffsetTime", type: "number", title: getFormat("noticable", "<b>Enter +/- Offset time from Sunrise or Sunset in minutes. Applies to all checked days for Switch.<br><small>EX: 30, -30, or -90, &nbsp Hit Enter</small>"), required: false, submitOnChange: true, accepts: "-1000 to 1000", range: "-1000..1000", width: 8, newLineAfter: true, style: 'margin-left:10px'
                    if (newOffsetTime) {
                        def (deviceId, scheduleId) = state.newOffsetTime.tokenize('|')
                        state.devices[deviceId].schedules[scheduleId].offset = newOffsetTime
                        state.remove("newOffsetTime")
                        app.removeSetting("newOffsetTime")
                        paragraph "<script>{changeSubmit(this)}</script>"
                    }
                }

                // Desired Level
                if (state.desiredLevel) {
                    input name: "desiredLevel", type: "number", title: getFormat("noticable", "<b>Enter a number between 1-100 for the desired dimmer level. Applies to all checked days for Dimmer.</b><br><small>EX: 1, 50, or 100, &nbsp Hit Enter</small>"), required: false, submitOnChange: true, accepts: "0 to 100", range: "0..100", width: 8, newLineAfter: true, style: 'margin-left:10px'
                    if (desiredLevel) {
                        def (deviceId, scheduleId) = state.desiredLevel.tokenize('|')
                        state.devices[deviceId].schedules[scheduleId].desiredLevel = desiredLevel
                        state.remove("desiredLevel")
                        app.removeSetting("desiredLevel")
                        paragraph "<script>{changeSubmit(this)}</script>"
                    }
                }

                if (state.selectVariableStartTime) {
                    input name: "newVariableTime", type: "enum", title: getFormat("noticable", "<b>Select Hub Variable</b>"), options: getHubVariableList(), required: false, submitOnChange: true, width: 8, newLineAfter: true, style: 'margin-left:10px'
                    if (newVariableTime) {
                        def (deviceId, scheduleId) = state.selectVariableStartTime.tokenize('|')
                        state.devices[deviceId].schedules[scheduleId].variableTime = newVariableTime
                        state.remove("selectVariableStartTime")
                        app.removeSetting("newVariableTime")
                        paragraph "<script>{changeSubmit(this)}</script>"
                    }
                }
            }
        }


//***  Advanced Inputs ***//

        section(getFormat("header", "Advanced Options"), hideable: true, hidden: false) {
            input "updateButton", "button", title: "Update/Store schedules without hitting Done"
            input name: "modeBool", type: "bool", title: getFormat("important2", "<b>Only run schedules during a selected mode?</b><br><small>Home, Away,.. Applies to all Devices</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            if (modeBool) {
                input name: "mode", type: "mode", title: getFormat("important2", "<b>Select mode(s) for schedules to run</b>"), defaultValue: false, submitOnChange: true, style: 'margin-left:70px', multiple: true
            }
            input name: "switchActivationBool", type: "bool", title: getFormat("important2", "<b>Only run schedules when a specific switch is set</b>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            if (switchActivationBool) {
                input name: "activationSwitch", type: "capability.switch", title: getFormat("important2", "Select required switch for activation"), submitOnChange: true, style: 'margin-left:70px', multiple: false, required: true
                input name: "activationSwitchOnOff", type: "enum", title: getFormat("important2", "on or off?"), submitOnChange: true, style: 'margin-left:70px', multiple: false, required: true, options: ["on", "off"]
            }
            input name: "activateOnBeforeLevelBool", type: "bool", title: getFormat("important2", "<b>Set 'on' before 'level'?</b><br><small>Use this option if a device does not turn on with a 'setLevel' command, but first needs to be turned on</small>"), defaultValue: false, submitOnChange: true, style: 'margin-left:10px'
            input name: "pauseBool", type: "bool", title: getFormat("important2","<b>Pause all schedules</b>"), defaultValue:false, submitOnChange:true, style: 'margin-left:10px'
            input name: "logEnableBool", type: "bool", title: getFormat("important2", "<b>Enable Logging of App based device activity and refreshes?</b><br><small>Shuts off in 1hr</small>"), defaultValue: true, submitOnChange: true, style: 'margin-left:10px'
        }


//****  Notes Section ****//

        section(getFormat("header", "Usage Notes"), hideable: true, hidden: hide) {
            paragraph getFormat("lessImportant", "<ul>" +
                    "<li>Use for any switch, outlet or dimmer. This may also include shades or others depending on your driver. Add as many as you want to the table.</li>" +
                    "<li>Enter Start time in 24 hour format.</li>" +
                    "<li>To use Sunset/Sunrise with +/- offset, check box, check sunrise or sunset icon, click number to enter offset.</li>" +
                    "<li>If you make/change a schedule, it wont take unless you hit 'Done' or 'Update/Store'.</li>" +
                    "<li>Optional: Select which modes to run schedules for.</li>" +
                    "<li>Optional: Select a switch that needs to be set on/off in order for schedules to run. This can be used as an override switch or essentailly a pause button for all schedules.</li>" +
                    "<li>Optional: Select whether you want to device to first receive an 'on' command before a 'setLevel' command. Useful if a device does not turn on via a 'setLevel' command.</li>" +
                    "<li>Optional: Select whether you want to pause all schedules.</li>" +
                    "</ul>")
        }
    }
}


//****  Main Table ****//

String displayTable() {
    // Sunday - Saturday Check Boxes
    if (state.sunCheckedBox) {
        def (deviceId, scheduleId) = state.sunCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sun = true
        state.remove("sunCheckedBox")
    } else if (state.sunUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sun = false
        state.remove("sunUnCheckedBox")
    }

    if (state.monCheckedBox) {
        def (deviceId, scheduleId) = state.monCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].mon = true
        state.remove("monCheckedBox")
    } else if (state.monUnCheckedBox) {
        def (deviceId, scheduleId) = state.monUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].mon = false
        state.remove("monUnCheckedBox")
    }

    if (state.tueCheckedBox) {
        def (deviceId, scheduleId) = state.tueCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].tue = true
        state.remove("tueCheckedBox")
    } else if (state.tueUnCheckedBox) {
        def (deviceId, scheduleId) = state.tueUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].tue = false
        state.remove("tueUnCheckedBox")
    }

    if (state.wedCheckedBox) {
        def (deviceId, scheduleId) = state.wedCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].wed = true
        state.remove("wedCheckedBox")
    } else if (state.wedUnCheckedBox) {
        def (deviceId, scheduleId) = state.wedUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].wed = false
        state.remove("wedUnCheckedBox")
    }

    if (state.thuCheckedBox) {
        def (deviceId, scheduleId) = state.thuCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].thu = true
        state.remove("thuCheckedBox")
    } else if (state.thuUnCheckedBox) {
        def (deviceId, scheduleId) = state.thuUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].thu = false
        state.remove("thuUnCheckedBox")
    }

    if (state.friCheckedBox) {
        def (deviceId, scheduleId) = state.friCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].fri = true
        state.remove("friCheckedBox")
    } else if (state.friUnCheckedBox) {
        def (deviceId, scheduleId) = state.friUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].fri = false
        state.remove("friUnCheckedBox")
    }

    if (state.satCheckedBox) {
        def (deviceId, scheduleId) = state.satCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sat = true
        state.remove("satCheckedBox")
    } else if (state.satUnCheckedBox) {
        def (deviceId, scheduleId) = state.satUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sat = false
        state.remove("satUnCheckedBox")
    }

    if (state.pauseCheckedBox) {
        def (deviceId, scheduleId) = state.pauseCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].pause = true
        state.remove("pauseCheckedBox")
    } else if (state.pauseUnCheckedBox) {
        def (deviceId, scheduleId) = state.pauseUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].pause = false
        state.remove("pauseUnCheckedBox")
    }

    // Sunrise/Sunset
    if (state.sunTimeCheckedBox) {
        def (deviceId, scheduleId) = state.sunTimeCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunTime = true
        state.remove("sunTimeCheckedBox")
    } else if (state.sunTimeUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunTimeUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunTime = false
        state.remove("sunTimeUnCheckedBox")
    }

    if (state.sunsetCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = true
        state.remove("sunsetCheckedBox")
    } else if (state.sunsetUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = false
        state.remove("sunsetUnCheckedBox")
    }

    // Variable time
    if (state.useVariableTimeChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeChecked.tokenize('|')
        logDebug "deviceId $deviceId; scheduleId: $scheduleId"
        logDebug "state.devices[deviceId]: ${state.devices[deviceId]}"
        logDebug "state.devices[deviceId].schedules: ${state.devices[deviceId].schedules}"
        logDebug "state.devices[deviceId].schedules[scheduleId]: ${state.devices[deviceId].schedules[scheduleId]}"
        state.devices[deviceId].schedules[scheduleId].useVariableTime = true
        state.remove("useVariableTimeChecked")
    } else if (state.useVariableTimeUnChecked) {
        def (deviceId, scheduleId) = state.useVariableTimeUnChecked.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].useVariableTime = false
        state.remove("useVariableTimeUnChecked")
    }

    if (state.sunsetCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = true
        state.remove("sunsetCheckedBox")
    } else if (state.sunsetUnCheckedBox) {
        def (deviceId, scheduleId) = state.sunsetUnCheckedBox.tokenize('|')
        state.devices[deviceId].schedules[scheduleId].sunset = false
        state.remove("sunsetUnCheckedBox")
    }

    // Add/Remove Run Times
    if (state.addRunTime) {
        def deviceId = state.addRunTime
        state.devices[deviceId].schedules[UUID.randomUUID().toString()] = generateDefaultSchedule()
        state.remove("addRunTime")
    }

    if (state.removeRunTime){
        def (deviceId, scheduleId) = state.removeRunTime.tokenize('|')
        state.devices[deviceId].schedules.remove(scheduleId)

        if (state.devices[deviceId].schedules.size() == 0) {
            state.devices[deviceId].schedules[UUID.randomUUID().toString()] = generateDefaultSchedule()
        }
        state.remove("removeRunTime")
    }

    // Type/Capability
    if (state.setCapabilityDimmer) {
        def deviceId = state.setCapabilityDimmer
        state.devices[deviceId].capability = "Dimmer"
        state.remove("setCapabilityDimmer")
    }

    if (state.setCapabilitySwitch) {
        def deviceId = state.setCapabilitySwitch
        state.devices[deviceId].capability = "Switch"
        state.remove("setCapabilitySwitch")
    }

    // Desired State
    if (state.desiredState) {
        def (deviceId, scheduleId) = state.desiredState.tokenize('|')
        logDebug "$deviceId|$scheduleId; ${state.devices[deviceId].schedules[scheduleId]}"
        if (state.devices[deviceId].schedules[scheduleId].desiredState == "on") {
            state.devices[deviceId].schedules[scheduleId].desiredState = "off"
        } else {
            state.devices[deviceId].schedules[scheduleId].desiredState = "on"
        }
        state.remove("desiredState")
    }

    // Table Header Build
    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
    str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td, .tstat-col th {font-size:15px !important; padding:2px 4px;text-align:center} + .tstat-col td {font-size:13px  }" +
            "</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:3px solid black'>" +
            "<thead><tr style='border-bottom:2px solid black'><th>#</th>" +
            "<th>Device</th>" +
            "<th>Current<br>State</th>" +
            "<th>Type</th>" +
            "<th style='border-right:2px solid black'>Add<br>Run</th>" +
            "<th style='width: 60px !important'>Run<br>Time</th>" +
            "<th>Use Hub<br>Variable?</th>" +
            "<th>Use Sun<br>Set/Rise?</th>" +
            "<th>Rise or<br>Set?</th>" +
            "<th>Offset<br>+/-Min</th>" +
            "<th>Sun</th>" + "<th>Mon</th>" + "<th>Tue</th>" + "<th>Wed</th>" + "<th>Thu</th>" + "<th>Fri</th>" + "<th>Sat</th>" +
            "<th>Pause<br>Schedule</th>" +
            "<th>Desired<br>State</th>" +
            "<th style='border-right:2px solid black'>Desired<br>Level</th>" +
            "<th style='border-right:2px solid black'>Remove<br>Run</th>" +
            "</tr></thead>"

    int zone = 0
    devices.sort { it.displayName.toLowerCase() }.each { dev ->
        zone += 1

        //**** Setup 'Device' Section of Table ****//

        String deviceLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
        String addNewRunButton = buttonLink("addNew|$dev.id", "+", "green", "23px")
        int thisZone = state.devices["$dev.id"].zone = zone
        int scheduleCount = state.devices["$dev.id"].schedules.size()
        boolean deviceIsDimmer = dev.capabilities.find { it.name == "SwitchLevel" } != null

        str += "<trstyle='color:black'><td rowspan='$scheduleCount'>$thisZone</td>" +
                "<td rowspan='$scheduleCount'>$deviceLink</td>"

        if (dev.currentSwitch) {
            str += "<td rowspan='$scheduleCount' title='Device is currently $dev.currentSwitch' style='color:${dev.currentSwitch == "on" ? "green" : "red"};font-weight:bold;font-size:23px'><iconify-icon icon='material-symbols:${dev.currentSwitch == "on" ? "circle" : "do-not-disturb-on-outline"}'></iconify-icon></td>"
        } else if (dev.currentValve) {
            str += "<td rowspan='$scheduleCount' style='color:${dev.currentValve == "open" ? "green" : "red"};font-weight:bold'><iconify-icon icon='material-symbols:do-not-disturb-on-outline'></iconify-icon></td>"
        }

        if (deviceIsDimmer) {
            String typeButton = (state.devices["$dev.id"].capability == "Switch") ? buttonLink("setCapabilityDimmer|$dev.id", "Switch", "MediumBlue") : buttonLink("setCapabilitySwitch|$dev.id", "Dimmer", "MediumBlue")
            str += "<td rowspan='$scheduleCount' style='font-weight:bold' title='Capability: Dimmer'>$typeButton</td>"
        } else {
            str += "<td rowspan='$scheduleCount' title='Capability: Switch'>Switch</td>"
        }

        str += "<td rowspan='$scheduleCount' style='border-right:2px solid black' title='Click to add new time for this device'>$addNewRunButton</td>"

        //**** Update sunrise/set times and order schedules ****//
        updateSunriseAndSet()

        def sortedSchedules = state.devices["$dev.id"].schedules.sort { a, b ->
            try {
                def parseDateTime = { dateStr ->
                    try {
                        // Try ISO format first
                        return new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", dateStr)
                    } catch (Exception e1) {
                        try {
                            // Try the other format
                            return new Date().parse("EEE MMM dd HH:mm:ss zzz yyyy", dateStr)
                        } catch (Exception e2) {
                            logError "Failed to parse date: ${dateStr}"
                            return null
                        }
                    }
                }

                def dateA = parseDateTime(a.value.startTime)
                def dateB = parseDateTime(b.value.startTime)
                if (dateA && dateB) {
                    def timeA = dateA.format('HH:mm')
                    def timeB = dateB.format('HH:mm')
                    return timeA <=> timeB
                }
                return 0
            } catch (Exception e) {
                logError "Error in sorting schedule dates: ${e.message}"
                return 0
            }
        }

        //**** Iterate each schedule, configuring the 'schedule' section of the table ****//

        sortedSchedules.each { scheduleId, schedule ->
            String thisStartTime = getTimeFromDateTimeString(schedule.startTime)
            String thisOffsetTime = schedule.offset
            String deviceAndScheduleId = "${dev.id}|$scheduleId"

            if (schedule.sunTime) {
                startTime = thisStartTime
            } else if (schedule.useVariableTime) {
                if (schedule.variableTime) {
                    def variableValue = getValueForHubVariable(schedule.variableTime)
                    if (variableValue == null) {
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "Select<br>Variable", "MediumBlue")
                    } else {
                        def dtString = ""
                        def date = getDateFromDateTimeString(schedule.startTime)
                        if (!date.equals("9999-99-99")) {
                            dtString += "$date"
                        }
                        def time = getTimeFromDateTimeString(schedule.startTime)
                        if (!time.equals("99:99")) {
                            if (dtString.length() > 0) {
                                dtString += "<br>"
                            }
                            dtString += time
                        }
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "${schedule.variableTime}<br>($dtString)", "MediumBlue")
                    }
                } else {
                    startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "Select<br>Variable", "MediumBlue")
                }
            } else {
                startTime = buttonLink("editStartTime|$deviceAndScheduleId", thisStartTime, "MediumBlue")
            }

            String sunCheckBoxT = (schedule.sun) ? buttonLink("sunUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("sunChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String monCheckBoxT = (schedule.mon) ? buttonLink("monUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("monChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String tueCheckBoxT = (schedule.tue) ? buttonLink("tueUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("tueChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String wedCheckBoxT = (schedule.wed) ? buttonLink("wedUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("wedChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String thuCheckBoxT = (schedule.thu) ? buttonLink("thuUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("thuChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String friCheckBoxT = (schedule.fri) ? buttonLink("friUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("friChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String satCheckBoxT = (schedule.sat) ? buttonLink("satUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "green", "23px") : buttonLink("satChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String pauseCheckBoxT = (schedule.pause) ? buttonLink("pauseUnChecked|$deviceAndScheduleId", "<iconify-icon icon=ic:sharp-pause-circle-filled></iconify-icon>", "red", "23px") : buttonLink("pauseChecked|$deviceAndScheduleId", "<iconify-icon icon=ic:baseline-play-circle></iconify-icon>", "green", "23px")
            String useVariableTimeCheckBoxT = (schedule.useVariableTime) ? buttonLink("useVariableTimeUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("useVariableTimeChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String sunTimeCheckBoxT = (schedule.sunTime) ? buttonLink("sunTimeUnChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box'></iconify-icon>", "purple", "23px") : buttonLink("sunTimeChecked|$deviceAndScheduleId", "<iconify-icon icon='material-symbols:check-box-outline-blank'></iconify-icon>", "black", "23px")
            String sunsetCheckBoxT = (schedule.sunset) ? buttonLink("sunsetUnChecked|$deviceAndScheduleId", "<iconify-icon icon=ph:moon-stars-duotone></iconify-icon>", "DodgerBlue", "23px") : buttonLink("sunsetChecked|$deviceAndScheduleId", "<iconify-icon icon='ph:sun-duotone'></iconify-icon>", "orange", "23px")
            String offset = buttonLink("newOffset|$deviceAndScheduleId", thisOffsetTime, "MediumBlue")
            String removeRunButton = buttonLink("removeRunTime|$deviceAndScheduleId", "x", "red", "23px")
            String desiredStateButton = buttonLink("desiredState|$deviceAndScheduleId", schedule.desiredState, "${schedule.desiredState == "on" ? "green" : "red"}", "15px; font-weight:bold")
            String desiredLevelButton = buttonLink("desiredLevel|$deviceAndScheduleId", schedule.desiredLevel.toString(), "MediumBlue")

            if (schedule.sunTime) {
                str += "<td title='Start Time with Sunset or Sunrise +/- offset'>$startTime</td>" +
                        "<td>Using Sun<br>Time</td>"
            } else if (schedule.useVariableTime){
                str += "<td title='Select Hub Variable'>$startTime</td>" +
                        "<td title='Use a hub variable'>$useVariableTimeCheckBoxT</td>"
            } else {
                str += "<td style='font-weight:bold !important' title='${thisStartTime ? "Click to Change Start Time" : "Select"}'>$startTime</td>" +
                        "<td title='Use a hub variable'>$useVariableTimeCheckBoxT</td>"
            }

            if (schedule.useVariableTime) {
                str += "<td colspan=2 title='Variable selected time (not sunset/sunrise)'>Hub Variable</td>" +
                       "<td style='font-weight:bold' title='${thisOffsetTime ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$offset</td>"
            } else {
                str += "<td title='Use Sunrise or Sunset for Start time'>$sunTimeCheckBoxT</td>"
                if (schedule.sunTime) {
                    str += "<td title='Sunset start (moon), otherwise Sunrise start(sun)'>$sunsetCheckBoxT</td>" +
                            "<td style='font-weight:bold' title='${thisOffsetTime ? "Click to set +/- minutes for Sunset or Sunrise start time" : "Select"}'>$offset</td>"
                } else {
                    str += "<td colspan=2 title='User Entered time (not sunset/sunrise)'>User Time</td>"
                }
            }

            str += "<td title='Check Box to select Day'>$sunCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$monCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$tueCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$wedCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$thuCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$friCheckBoxT</td>" +
                    "<td title='Check Box to select Day'>$satCheckBoxT</td>" +
                    "<td title='Check Box to Pause this device schedule, Red is paused, Green is run'>$pauseCheckBoxT</td>" +
                    "<td title='Click to change desired state'>$desiredStateButton</td>"

            if (state.devices["$dev.id"].capability == "Switch" || schedule.desiredState == "off") {
                str += "<td style='border-right:2px solid black'></td>"
            } else {
                str += "<td style='border-right:2px solid black; font-weight:bold' title='Click to set dimmer level'>$desiredLevelButton</td>"
            }

            str += "<td title='Click to remove run'>$removeRunButton</td></tr>"
        }
    }
    str += "</table></div>"
    return str
}


//**** Handlers ****//

void switchHandler(data) {
    if (logEnableBool) logDebug "switchHandler - data: $data"

    // If this is set, crons should not even be scheduled but we'll check anyways
    if (pauseBool) {
        if (logEnableBool) logDebug "All schedules have been manually paused. Skipping."
        return
    }

    def deviceConfig = state.devices[data.deviceId]
    def schedule = deviceConfig.schedules[data.scheduleId]
    def device = devices.find { it.id == data.deviceId }

    if ((modeBool && mode.contains(location.mode)) || (!modeBool)) {
        if ((switchActivationBool && activationSwitch.currentSwitch == activationSwitchOnOff) || (!switchActivationBool)) {
            if (!schedule.pause) { // If schedule is paused it should never be scheduled anyway. We'll still check.
                // Check to make sure the date is correct if we're using Hub Variables
                def validDate = true
                if (schedule.useVariableTime && schedule.variableTime != null) {
                    def dateStr = getDateFromDateTimeString(schedule.startTime)
                    if (!dateStr.equals("9999-99-99")){ // this indicates the Hub Variable is only a time, not datetime
                        def parsedDate = Date.parse('yyyy-MM-dd', dateStr).clearTime()
                        validDate = new Date().clearTime() == parsedDate
                    }
                }

                if (validDate) {
                    logDebug "switchHandler - Device: $device; schedule: $schedule"
                    if (schedule.desiredState == "on") {
                        if (deviceConfig.capability == "Dimmer") {
                            if (activateOnBeforeLevelBool) {
                                device.on()
                                logDebug "$device turned on"
                            }
                            device.setLevel(schedule.desiredLevel)
                            logDebug "$device set to $schedule.desiredLevel"
                        } else {
                            device.on()
                            logDebug "$device turned on"
                        }
                    } else {
                        device.off()
                        logDebug "$device turned off"
                    }
                } else {
                    logDebug "Scheduled date is in the past, skipping run for Device: $device; schedule: $schedule"
                }
            } else {
                // If schedule is paused it should never be scheduled anyway
                logDebug "Schedule is paused, skipping run for Device: $device; schedule: $schedule"
            }
        } else {
            logDebug "Switch $activationSwitch is not set to $activationSwitchOnOff, skipping run for Device: $device; schedule: $schedule"
        }
    } else {
        logDebug "Mode of $location.mode is not one of $mode, skipping run for Device: $device; schedule: $schedule"
    }
}

// Get new sunrise/sunset and Hub Variable times
def refreshVariables(evt) {
    logDebug "refreshVariables called"
    updateSunriseAndSet()
    updateVariableTimes()
}

def logsOff() {
    logDebug "logsOff() called - All App logging auto disabled"
    app?.updateSetting("logEnableBool", [value: "false", type: "bool"])
}

void appButtonHandler(btn) {
    if (btn == "updateButton") updated()
    else if (btn.startsWith("sunUnChecked|")) state.sunUnCheckedBox = btn.minus("sunUnChecked|")
    else if (btn.startsWith("sunChecked|")) state.sunCheckedBox = btn.minus("sunChecked|")
    else if (btn.startsWith("monUnChecked|")) state.monUnCheckedBox = btn.minus("monUnChecked|")
    else if (btn.startsWith("monChecked|")) state.monCheckedBox = btn.minus("monChecked|")
    else if (btn.startsWith("tueUnChecked|")) state.tueUnCheckedBox = btn.minus("tueUnChecked|")
    else if (btn.startsWith("tueChecked|")) state.tueCheckedBox = btn.minus("tueChecked|")
    else if (btn.startsWith("wedUnChecked|")) state.wedUnCheckedBox = btn.minus("wedUnChecked|")
    else if (btn.startsWith("wedChecked|")) state.wedCheckedBox = btn.minus("wedChecked|")
    else if (btn.startsWith("thuUnChecked|")) state.thuUnCheckedBox = btn.minus("thuUnChecked|")
    else if (btn.startsWith("thuChecked|")) state.thuCheckedBox = btn.minus("thuChecked|")
    else if (btn.startsWith("friUnChecked|")) state.friUnCheckedBox = btn.minus("friUnChecked|")
    else if (btn.startsWith("friChecked|")) state.friCheckedBox = btn.minus("friChecked|")
    else if (btn.startsWith("satUnChecked|")) state.satUnCheckedBox = btn.minus("satUnChecked|")
    else if (btn.startsWith("satChecked|")) state.satCheckedBox = btn.minus("satChecked|")
    else if (btn.startsWith("editStartTime|")) state.newStartTime = btn.minus("editStartTime|")
    else if (btn.startsWith("pauseUnChecked|")) state.pauseUnCheckedBox = btn.minus("pauseUnChecked|")
    else if (btn.startsWith("pauseChecked|")) state.pauseCheckedBox = btn.minus("pauseChecked|")
    else if (btn.startsWith("sunTimeUnChecked|")) state.sunTimeUnCheckedBox = btn.minus("sunTimeUnChecked|")
    else if (btn.startsWith("sunTimeChecked|")) state.sunTimeCheckedBox = btn.minus("sunTimeChecked|")
    else if (btn.startsWith("sunsetUnChecked|")) state.sunsetUnCheckedBox = btn.minus("sunsetUnChecked|")
    else if (btn.startsWith("sunsetChecked|")) state.sunsetCheckedBox = btn.minus("sunsetChecked|")
    else if (btn.startsWith("newOffset|")) state.newOffsetTime = btn.minus("newOffset|")
    else if (btn.startsWith("addNew|")) state.addRunTime = btn.minus("addNew|")
    else if (btn.startsWith("removeRunTime|")) state.removeRunTime = btn.minus("removeRunTime|")
    else if (btn.startsWith("setCapabilityDimmer|")) state.setCapabilityDimmer = btn.minus("setCapabilityDimmer|")
    else if (btn.startsWith("setCapabilitySwitch|")) state.setCapabilitySwitch = btn.minus("setCapabilitySwitch|")
    else if (btn.startsWith("desiredState|")) state.desiredState = btn.minus("desiredState|")
    else if (btn.startsWith("desiredLevel|")) state.desiredLevel = btn.minus("desiredLevel|")
    else if (btn.startsWith("useVariableTimeUnChecked|")) state.useVariableTimeUnChecked = btn.minus("useVariableTimeUnChecked|")
    else if (btn.startsWith("useVariableTimeChecked|")) state.useVariableTimeChecked = btn.minus("useVariableTimeChecked|")
    else if (btn.startsWith("selectVariableStartTime|")) state.selectVariableStartTime = btn.minus("selectVariableStartTime|")
}


//**** Functions ****//

def updateSunriseAndSet() {
    devices.each { dev ->
        state.devices["$dev.id"].schedules.each { scheduleId, schedule ->
            if (schedule.sunTime) {
                def offsetRiseAndSet = getSunriseAndSunset(sunriseOffset: schedule.offset, sunsetOffset: schedule.offset)
                if (schedule.sunset) {
                    schedule.startTime = offsetRiseAndSet.sunset.toString()
                } else {
                    schedule.startTime = offsetRiseAndSet.sunrise.toString()
                }
            }
        }
    }
}

def variableChangeHandler(evt) {
    logDebug "Hub Variable Changed: ${evt.name} -> ${evt.value}"
    updated()
}

def updateVariableTimes() {
    devices.each { dev ->
        state.devices["$dev.id"].schedules.each { scheduleId, schedule ->
            if (schedule.useVariableTime && schedule.variableTime) {
                def varTime = getGlobalVar(schedule.variableTime)
                if (varTime != null) {
                    // If the Hub Variable just a date, then the time will be 99:99:99.999. Replace that with midnight.
                    def startTime = varTime.value.replace("99:99:99.999", "00:00:00.000")
                    if (schedule.offset && schedule.offset != 0) {
                        def date = new Date().parse("yyyy-MM-dd'T'HH:mm:ss.sssXX", startTime)
                        date = new Date(date.getTime() + (schedule.offset * 60 * 1000L)) // convert from minutes to seconds to milliseconds
                        startTime = date.format("yyyy-MM-dd'T'HH:mm:ss.sssXX")
                    }
                    schedule.startTime = startTime
                    schedule.startTime = startTime
                }
            }
        }
    }
}

def buildCron() {
    logDebug "buildCron called"

    state.devices.each { deviceId, deviceConfigs ->
        deviceConfigs.schedules.each { scheduleId, schedule ->
            if (schedule.startTime) {
                String formattedTime = getTimeFromDateTimeString(schedule.startTime)
                String hours = formattedTime.substring(0, formattedTime.length() - 3) // Chop off the last 3 in string
                String minutes = formattedTime.substring(3) // Chop off the first 3 in string

                String days = ""
                if (schedule.sun) days = "SUN,"
                if (schedule.mon) days += "MON,"
                if (schedule.tue) days += "TUE,"
                if (schedule.wed) days += "WED,"
                if (schedule.thu) days += "THU,"
                if (schedule.fri) days += "FRI,"
                if (schedule.sat) days += "SAT,"

                if (days != "") {
                    days = days.substring(0, days.length() - 1) // Chop off last ","
                    schedule.days = days
                    schedule.cron = "0 ${minutes} ${hours} ? * ${days} *"
                    logDebug "deviceId: $deviceId; scheduleId: $scheduleId; Generated cron: ${schedule.cron}"
                }
            }
        }
    }
}

private getHubVariableList() {
    def variables = [:]

    // Create a map of variable names to their name & value concatenation
    def allVariables = getGlobalVarsByType("datetime")
    allVariables.each { key, value ->
        variables[key] = "$key: ${value.value}"
    }
    return variables
}

private getValueForHubVariable(name) {
    def val = getGlobalVar(name)
    if (val != null) {
        return val.value
    }
    return null
}

private getTimeFromDateTimeString(dt) {
    return dt.substring(11, dt.length() - 12)
}

private getDateFromDateTimeString(dt) {
    return dt.tokenize("T")[0]
}

// Formating function for consistency and ease of changing style
def getFormat(type, myText = "") {
    if (type == "title") return "<h3 style='color:SteelBlue; font-weight: bold'>${myText}</h3>"  // Steel-Blue
    if (type == "blueRegular") return "<div style='color:SteelBlue; font-weight: bold'>${myText}</div>"  // Steel-Blue
    if (type == "noticable") return "<div style='color:#CC5500'>${myText}</div>"  // Burnt-Orange
    if (type == "important") return "<div style='color:#32a4be'>${myText}</div>"  // Flat Tourquise
    if (type == "lessImportant") return "<div style='color:green'>${myText}</div>" // Green
    if (type == "header") return "<div style='color:#000000;font-weight: bold'>${myText}</div>"  // Black
    if (type == "important2") return "<div style='color:#000000'>${myText}</div>"   // Black
}

// Helper to generate & format a button
String buttonLink(String btnName, String linkText, color = SteelBlue, font = "15px") {
    "<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

// Generate a new, empty schedule
static LinkedHashMap<String, Object> generateDefaultSchedule() {
    return [
            sunTime  : false,
            sunset   : true,
            offset   : 0,
            sun      : true,
            mon      : true,
            tue      : true,
            wed      : true,
            thu      : true,
            fri      : true,
            sat      : true,
            startTime: "00000000000Select000000000000",
            cron     : "",
            days     : "",
            pause    : false,
            desiredState: "on",
            desiredLevel: 100,
            useVariableTime: false,
            variableTime: null
    ]
}

def displayTitle() {
    titleVersion()

    String str = ""
    if (pauseBool) {
        str += getFormat("noticable", "<h3>[App Paused]</h3>")
    }
    str += getFormat("blueRegular", "${appName ? appName + ' - ' : ""}${state.name}: ${"ver " + state.version}")
    section (str) {}
}

void logDebug(msg) {
    if (logEnableBool) {
        log.debug("${app.label} - $msg")
    }
}

void logError(msg) {
    if (logEnableBool) {
        log.error("${app.label} - $msg")
    }
}


//**** Required Methods ****//

void initialize() {
    logDebug "initialize() called"

    if (logEnableBool) runIn(3600, logsOff)  // Disable all Logging after time elapsed

    if (pauseBool) {
        logDebug "All schedules have been manually paused. Will skip scheduling"
        return
    }

    schedule("0 0 1 ? * * *", updated) // Every day at 1am update schedules (refreshes sun rise/set and Hub Variables)

    logDebug state.devices

    // Set device cron schedules
    if (devices != null) {
        devices.sort { it.displayName.toLowerCase() }.each { dev ->
            deviceConfig = state.devices["$dev.id"]
            if (deviceConfig != null) { // Can happen when adding a new device that doesn't yet have a default config
                zone = deviceConfig.zone
                deviceConfig.schedules.each { scheduleId, sched ->
                    if (sched.cron && !sched.pause && !sched.cron.contains("Sel")) {
                        schedule(sched.cron, switchHandler, [data:[deviceId: dev.id, scheduleId: scheduleId], overwrite:false])
                        logDebug "SCHEDULED Device: $dev; DeviceId: $dev.id; ScheduleId: $scheduleId; deviceSchedule: $sched"

                        // Subscribe to Hub Variable Change Events & Register Variable Use with Hub
                        if (sched.useVariableTime && sched.variableTime != null) {
                            addInUseGlobalVar(sched.variableTime)
                            subscribe(location, "variable:${sched.variableTime}", "variableChangeHandler")
                            logDebug "Subscribed to Hub Variable Change events for variable: ${sched.variableTime}"
                        }
                    }
                }
            }
        }
    }
}

// Required handler for when a hub variable is renamed
def renameVariable(oldName, newName) {
    logDebug "renameVariable called - old: $oldName, new: $newName"
    if (devices != null) {
        devices.each { dev ->
            deviceConfig = state.devices["$dev.id"]
            if (deviceConfig != null) { // Can happen when adding a new device that doesn't yet have a default config
                zone = deviceConfig.zone
                deviceConfig.schedules.each { scheduleId, sched ->
                    if (sched != null && sched.variableTime != null && sched.variableTime.equals(oldName)) {
                        sched.variableTime = newName
                    }
                }
            }
        }
    }
}

def updated() {  // runs every 'Done' on already installed app
    logDebug "updated called"
    unsubscribe()
    unschedule()  // cancels all (or one) scheduled jobs including runIn, switchHandler, refreshVariables
    removeAllInUseGlobalVar()  // remove all in use global Hub Variables
    refreshVariables()
    buildCron()  // build schedules
    initialize()  // set schedules and subscribes
}

def installed() {  // only runs once for new app 'Done' or first time open
    logDebug "installed called"
    updated()
}
