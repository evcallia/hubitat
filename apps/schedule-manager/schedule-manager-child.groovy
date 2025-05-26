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
 *  2.0.2 - 2025-04-21 - Fix error when offset is applied to "time" or "date" only Hub Variables
 *
 */

import groovy.json.JsonOutput
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.ZonedDateTime

def titleVersion() {
    state.name = "Schedule Manager"
    state.version = "2.0.1"
}

definition(
        name: "Schedule Manager (Child App)",
        label: "Schedule Manager Instance",
        namespace: "evcallia-dev",
        author: "Evan Callia",
        description: "Child app for schedule manager",
        category: "Control",
        parent: "evcallia-dev:Schedule Manager",
        iconUrl: "",
        iconX2Url: "",
        oauth: true
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

//****  API Routes ****//
mappings {
    path("/updateTime") {
        action: [POST: "updateTime"]
    }

    path("/updateOffset") {
        action: [POST: "updateOffset"]
    }

    path("/updateDesiredLevel") {
        action: [POST: "updateDesiredLevel"]
    }

    path("/updateHubVariable") {
        action: [POST: "updateHubVariable"]
    }

    path("/getHubVariableOptions") {
        action: [GET: "getHubVariableOptions"]
    }
}

//****  API Route Handlers ****//
def updateTime() {
    logDebug "updateTime called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def newStartTime = json.startTime
    def zdt = ZonedDateTime.now().withHour(newStartTime.split(':')[0].toInteger())
                                .withMinute(newStartTime.split(':')[1].toInteger())
                                .withSecond(0)
                                .withNano(0)
    def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    state.devices[deviceId].schedules[scheduleId].startTime = zdt.format(formatter)
}

def updateOffset() {
    logDebug "updateOffset called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def newOffset = json.offset.toInteger()
    state.devices[deviceId].schedules[scheduleId].offset = newOffset
    refreshVariables()
    if (state.devices[deviceId].schedules[scheduleId].useVariableTime) {
        render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(state.devices[deviceId].schedules[scheduleId].variableTime, state.devices[deviceId].schedules[scheduleId].startTime), offset: newOffset, varType: "hubVariable"])
    } else {
        def newStartTime = getTimeFromDateTimeString(state.devices[deviceId].schedules[scheduleId].startTime)
        render contentType: "application/json", data: JsonOutput.toJson([startTime: newStartTime, offset: newOffset, varType: "sun"])
    }
}

def updateDesiredLevel() {
    logDebug "updateDesiredLevel called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def newLevel = json.level.toInteger()
    state.devices[deviceId].schedules[scheduleId].desiredLevel = newLevel
}

def updateHubVariable() {
    logDebug "updateHubVariable called with args ${request.JSON}"
    def json = request.JSON
    def deviceId = json.deviceId
    def scheduleId = json.scheduleId
    def newVariable = json.hubVariable
    state.devices[deviceId].schedules[scheduleId].variableTime = newVariable
    updateVariableTimes()
    render contentType: "application/json", data: JsonOutput.toJson([startTime: formatHubVariableNameWithTime(newVariable, state.devices[deviceId].schedules[scheduleId].startTime)])
}

def getHubVariableOptions() {
    logDebug "getHubVariableOptions called"
    render contentType: "application/json", data: JsonOutput.toJson(getHubVariableList())
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

    // Configure table, scripts and css
    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
    str += """<style>
            .mdl-data-table {
                width: 100%;
                border-collapse: collapse;
                border: 1px solid #E0E0E0;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                border-radius: 4px;
                overflow: hidden;
                margin-bottom: 20px;
            }
            .mdl-data-table thead {
                background-color: #F5F5F5;
            }
            .mdl-data-table th {
                font-size: 14px !important;
                font-weight: 500;
                color: #424242;
                padding: 8px !important;
                text-align: center;
                border-bottom: 2px solid #E0E0E0;
                border-right: 1px solid #E0E0E0;
            }
            .mdl-data-table td {
                font-size: 14px !important;
                padding: 6px 4px !important;
                text-align: center;
                border-bottom: 1px solid #EEEEEE;
                border-right: 1px solid #EEEEEE;
            }
            .mdl-data-table tbody tr:hover {
                background-color: inherit !important;
            }
            .device-section {
                font-weight: 500;
            }
            .device-link a {
                color: #2196F3;
                text-decoration: none;
                font-weight: 500;
            }
            .device-link a:hover {
                text-decoration: underline;
            }

            /* Popup styles */
            .popup-overlay {
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0, 0, 0, 0.5);
                display: none;
                justify-content: center;
                align-items: center;
                z-index: 1000;
            }
            .popup-container {
                background-color: #FFFFFF;
                border-radius: 8px;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
                padding: 20px;
                max-width: 400px;
                width: 90%;
                position: relative;
            }
            .popup-title {
                font-size: 18px;
                font-weight: 500;
                color: #212121;
                margin-bottom: 15px;
                padding-bottom: 10px;
                border-bottom: 1px solid #EEEEEE;
            }
            .popup-content {
                margin-bottom: 20px;
            }
            .popup-buttons {
                display: flex;
                justify-content: flex-end;
                gap: 10px;
            }
            .popup-btn {
                padding: 8px 16px;
                border-radius: 4px;
                font-size: 14px;
                font-weight: 500;
                cursor: pointer;
                border: none;
                outline: none;
            }
            .popup-btn-primary {
                background-color: #2196F3;
                color: white;
            }
            .popup-btn-secondary {
                background-color: #E0E0E0;
                color: #424242;
            }
            .popup-close {
                position: absolute;
                top: 15px;
                right: 15px;
                font-size: 20px;
                cursor: pointer;
                color: #9E9E9E;
            }
            .popup-input {
                width: 100%;
                padding: 10px;
                border-radius: 4px;
                border: 1px solid #E0E0E0;
                font-size: 14px;
                margin-bottom: 10px;
            }
            .popup-label {
                display: block;
                font-size: 14px;
                color: #616161;
                margin-bottom: 5px;
            }
            </style>

            <script>
            // Popup system
            function showPopup(title, content, submitCallback) {
                // Create popup elements
                const overlay = document.createElement('div');
                overlay.className = 'popup-overlay';

                const container = document.createElement('div');
                container.className = 'popup-container';

                const closeBtn = document.createElement('div');
                closeBtn.className = 'popup-close';
                closeBtn.innerHTML = '&times;';
                closeBtn.onclick = hidePopup;

                const titleEl = document.createElement('div');
                titleEl.className = 'popup-title';
                titleEl.innerText = title;

                const contentEl = document.createElement('div');
                contentEl.className = 'popup-content';
                contentEl.innerHTML = content;

                const buttonsEl = document.createElement('div');
                buttonsEl.className = 'popup-buttons';

                const cancelBtn = document.createElement('button');
                cancelBtn.className = 'popup-btn popup-btn-secondary';
                cancelBtn.innerText = 'Cancel';
                cancelBtn.onclick = hidePopup;

                const submitBtn = document.createElement('button');
                submitBtn.className = 'popup-btn popup-btn-primary';
                submitBtn.innerText = 'Submit';
                submitBtn.onclick = function() {
                    submitCallback(overlay);
                };

                // Build popup structure
                buttonsEl.appendChild(cancelBtn);
                buttonsEl.appendChild(submitBtn);

                container.appendChild(closeBtn);
                container.appendChild(titleEl);
                container.appendChild(contentEl);
                container.appendChild(buttonsEl);

                overlay.appendChild(container);

                // Add to document and show
                document.body.appendChild(overlay);
                setTimeout(() => {
                    overlay.style.display = 'flex';
                    // Focus the first input if exists
                    const firstInput = overlay.querySelector('input, select');
                    if (firstInput) firstInput.focus();
                }, 10);

                return overlay;
            }

            function hidePopup() {
                const overlays = document.querySelectorAll('.popup-overlay');
                overlays.forEach(overlay => {
                    overlay.style.display = 'none';
                    setTimeout(() => {
                        overlay.remove();
                    }, 300);
                });
            }

            // Time input popup
            function editStartTimePopup(deviceId, scheduleId, currentValue) {
                const content = `
                    <label class="popup-label">Enter Desired Time</label>
                    <input type="time" class="popup-input" id="timeInput" value="${currentValue || ''}">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Applies to all checked days for this device.</p>
                `;

                showPopup("Set Schedule Time", content, (popup) => {
                    const input = popup.querySelector('#timeInput');
                    if (input.value) {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateTime?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            startTime: input.value
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                document.getElementById("editStartTime|" + deviceId + "|" + scheduleId).innerHTML = timeInput.value;
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }

            // Offset input popup
            function newOffsetPopup(deviceId, scheduleId, currentValue) {
                const content = `
                    <label class="popup-label">Enter +/- Offset time (in minutes)</label>
                    <input type="number" class="popup-input" id="offsetInput" value="${currentValue || '0'}">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Examples: 30, -30, or -90. Applied to Sunrise/Sunset or Hub Variable time.</p>
                `;

                showPopup("Set Time Offset", content, (popup) => {
                    const input = popup.querySelector('#offsetInput');
                    if (input.value !== '') {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateOffset?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            offset: input.value
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                const jsonResponse = JSON.parse(xhr.responseText);
                                const newStartTime = jsonResponse.startTime;
                                const varType = jsonResponse.varType;

                                var idPrefix = ""
                                if (varType === "hubVariable") {
                                    idPrefix = "selectVariableStartTime|";
                                } else {
                                    idPrefix = "editStartTime|";
                                }

                                document.getElementById("newOffset|" + deviceId + "|" + scheduleId).innerHTML = input.value;
                                document.getElementById(idPrefix + deviceId + "|" + scheduleId).innerHTML = newStartTime;
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }

            // Dimmer level popup
            function desiredLevelPopup(deviceId, scheduleId, currentValue) {
                const content = `
                    <label class="popup-label">Enter Dimmer Level (1-100)</label>
                    <input type="number" class="popup-input" id="levelInput" value="${currentValue || '100'}" min="0" max="100">
                    <p style="font-size:12px;color:#757575;margin-top:5px;">Set the desired brightness level when the device turns on.</p>
                `;

                showPopup("Set Dimmer Level", content, (popup) => {
                    const input = popup.querySelector('#levelInput');
                    if (input.value !== '' && input.value >= 0 && input.value <= 100) {
                        // Send request to hubitat app api to handle state change
                        var xhr = new XMLHttpRequest();
                        xhr.open('POST', '/apps/api/${app.id}/updateDesiredLevel?access_token=${state.accessToken}', true);
                        xhr.setRequestHeader('Content-Type', 'application/json');

                        var data = JSON.stringify({
                            deviceId: deviceId,
                            scheduleId: scheduleId,
                            level: input.value
                        });

                        xhr.onreadystatechange = function() {
                            if (xhr.readyState === 4 && xhr.status === 200) {
                                // When successful, update the input field in the table so we don't need to page refresh
                                document.getElementById("desiredLevel|" + deviceId + "|" + scheduleId).innerHTML = input.value;
                            }
                        };

                        xhr.send(data);
                    }
                    hidePopup();
                });
            }

            // Hub Variable popup
            function selectVariableStartTimePopup(deviceId, scheduleId, currentValue) {
                const fetchOptions = () => {
                    return fetch('/apps/api/${app.id}/getHubVariableOptions?access_token=${state.accessToken}')
                        .then(response => response.json())
                        .catch(error => {
                            console.error('Error fetching variable options:', error);
                            return {};
                        });
                };

                fetchOptions().then(options => {
                    console.log("fetched options: " + options)
                    // Create a select dropdown from the options
                    let optionsHtml = '<label class="popup-label">Select Hub Variable</label>';
                    optionsHtml += '<select class="popup-input" id="variableSelect">';

                    // Add options from the server
                    for (const [key, value] of Object.entries(options)) {
                        const selected = currentValue === key ? 'selected' : '';
                        optionsHtml += '<option value="' + key + '" ' + selected + '>' + value + '</option>';
                    }

                    optionsHtml += '</select>';
                    optionsHtml += '<p style="font-size:12px;color:#757575;margin-top:5px;">Select a Hub Variable to use as the schedule time.</p>';

                    showPopup("Select Hub Variable", optionsHtml, (popup) => {
                        const input = popup.querySelector('#variableSelect');
                        if (input.value) {
                            // Send request to hubitat app api to handle state change
                            var xhr = new XMLHttpRequest();
                            xhr.open('POST', '/apps/api/${app.id}/updateHubVariable?access_token=${state.accessToken}', true);
                            xhr.setRequestHeader('Content-Type', 'application/json');

                            var data = JSON.stringify({
                                deviceId: deviceId,
                                scheduleId: scheduleId,
                                hubVariable: input.value
                            });

                            xhr.onreadystatechange = function() {
                                if (xhr.readyState === 4 && xhr.status === 200) {
                                    // When successful, update the input field in the table so we don't need to page refresh
                                    const jsonResponse = JSON.parse(xhr.responseText);
                                    const newStartTime = jsonResponse.startTime;

                                    document.getElementById("selectVariableStartTime|" + deviceId + "|" + scheduleId).innerHTML = newStartTime;
                                }
                            };

                            xhr.send(data);
                        }
                        hidePopup();
                    });
                });
            }
            </script>

            <div style='overflow-x:auto'><table class='mdl-data-table'>""" +
            "<thead><tr><th>#</th>" +
            "<th>Device</th>" +
            "<th>Current<br>State</th>" +
            "<th>Type</th>" +
            "<th style='border-right:1px solid gray'>Add<br>Run</th>" +
            "<th style='width: 60px !important'>Run<br>Time</th>" +
            "<th>Use Hub<br>Variable?</th>" +
            "<th>Use Sun<br>Set/Rise?</th>" +
            "<th>Rise or<br>Set?</th>" +
            "<th>Offset<br>+/-Min</th>" +
            "<th>Sun</th>" + "<th>Mon</th>" + "<th>Tue</th>" + "<th>Wed</th>" + "<th>Thu</th>" + "<th>Fri</th>" + "<th>Sat</th>" +
            "<th>Pause<br>Schedule</th>" +
            "<th>Desired<br>State</th>" +
            "<th style='border-right:1px solid gray'>Desired<br>Level</th>" +
            "<th>Remove<br>Run</th>" +
            "</tr></thead>"

    int zone = 0
    devices.sort { it.displayName.toLowerCase() }.each { dev ->
        zone += 1

        //**** Setup 'Device' Section of Table ****//

        String deviceLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev</a>"
        String addNewRunButton = buttonLink("addNew|$dev.id", "<iconify-icon icon='material-symbols:add-circle-outline-rounded'></iconify-icon>", "#4CAF50", "24px")
        int thisZone = state.devices["$dev.id"].zone = zone
        int scheduleCount = state.devices["$dev.id"].schedules.size()
        boolean deviceIsDimmer = dev.capabilities.find { it.name == "SwitchLevel" } != null

        str += "<tr class='device-section'><td rowspan='$scheduleCount'>$thisZone</td>" +
                "<td rowspan='$scheduleCount' class='device-link'>$deviceLink</td>"

        if (dev.currentSwitch) {
            str += "<td rowspan='$scheduleCount' title='Device is currently $dev.currentSwitch' style='color:${dev.currentSwitch == "on" ? "#4CAF50" : "#F44336"};font-weight:bold;font-size:24px'><iconify-icon icon='material-symbols:${dev.currentSwitch == "on" ? "circle" : "do-not-disturb-on-outline"}'></iconify-icon></td>"
        } else if (dev.currentValve) {
            str += "<td rowspan='$scheduleCount' style='color:${dev.currentValve == "open" ? "#4CAF50" : "#F44336"};font-weight:bold'><iconify-icon icon='material-symbols:do-not-disturb-on-outline'></iconify-icon></td>"
        }

        if (deviceIsDimmer) {
            String typeButton = (state.devices["$dev.id"].capability == "Switch") ? buttonLink("setCapabilityDimmer|$dev.id", "Switch", "#2196F3") : buttonLink("setCapabilitySwitch|$dev.id", "Dimmer", "#2196F3")
            str += "<td rowspan='$scheduleCount' style='font-weight:bold' title='Capability: Dimmer'>$typeButton</td>"
        } else {
            str += "<td rowspan='$scheduleCount' title='Capability: Switch'>Switch</td>"
        }

        str += "<td rowspan='$scheduleCount' style='border-right:1px solid gray' title='Click to add new time for this device'>$addNewRunButton</td>"

        //**** Update sunrise/set & hub variable times ****//
        refreshVariables()

        // order schedules so table is sorted by time
        def sortedSchedules = state.devices["$dev.id"].schedules.sort { a, b ->
            try {
                def parseDateTime = { dateStr ->
                    if (dateStr == "00000000000Select000000000000") {
                        return null
                    }

                    try {
                        // Try ISO format first
                        def todayDateString = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        dateStr = dateStr.replace("yyyy-mm-dd", todayDateString).replace("9999-99-99", todayDateString).replace("sss-zzzz", "000-0000")
                        return new Date().parse("yyyy-MM-dd'T'HH:mm:ss.SSSZ", dateStr)
                    } catch (Exception e1) {
                        try {
                            // Try the other format
                            return new Date().parse("EEE MMM dd HH:mm:ss zzz yyyy", dateStr)
                        } catch (Exception e2) {
                            logError "Failed to parse date: ${dateStr}. Exception 1: ${e1.message}, Exception 2: ${e2.message}"
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
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", "Select Variable", "MediumBlue")
                    } else {
                        startTime = buttonLink("selectVariableStartTime|$deviceAndScheduleId", formatHubVariableNameWithTime(schedule.variableTime, schedule.startTime), "MediumBlue")
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
                str += "<td id='editStartTime|${deviceAndScheduleId}' title='Start Time with Sunset or Sunrise +/- offset'>$startTime</td>" +
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
                str += "<td style='border-right:1px solid gray'></td>"
            } else {
                str += "<td style='border-right:1px solid gray; font-weight:bold' title='Click to set dimmer level'>$desiredLevelButton</td>"
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
    else if (btn.startsWith("addNew|")) state.addRunTime = btn.minus("addNew|")
    else if (btn.startsWith("removeRunTime|")) state.removeRunTime = btn.minus("removeRunTime|")
    else if (btn.startsWith("setCapabilityDimmer|")) state.setCapabilityDimmer = btn.minus("setCapabilityDimmer|")
    else if (btn.startsWith("setCapabilitySwitch|")) state.setCapabilitySwitch = btn.minus("setCapabilitySwitch|")
    else if (btn.startsWith("desiredState|")) state.desiredState = btn.minus("desiredState|")
    else if (btn.startsWith("useVariableTimeUnChecked|")) state.useVariableTimeUnChecked = btn.minus("useVariableTimeUnChecked|")
    else if (btn.startsWith("useVariableTimeChecked|")) state.useVariableTimeChecked = btn.minus("useVariableTimeChecked|")
}


//**** Functions ****//

def updateSunriseAndSet() {
    devices.each { dev ->
        state.devices["$dev.id"].schedules.each { scheduleId, schedule ->
            if (schedule.sunTime) {
                logDebug "Updating Sunrise/Sunset for Device: $dev; Schedule: $scheduleId, Offset: ${schedule.offset}"
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
                    // If the Hub Variable is just a date, then the time will be 99:99:99.999-9999. Replace that with midnight of current timezone.
                    def startTime = varTime.value.replace("99:99:99.999-9999", "00:00:00.000" + new Date().format("XX"))
                    if (schedule.offset && schedule.offset != 0) {
                        dateStr = getDateFromDateTimeString(startTime)
                        def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                        def date = ZonedDateTime.parse(startTime.replace("9999-99-99", "2000-01-01"), formatter) // Random date substitute, doesn't matter
                        date = date.plusMinutes(schedule.offset)

                        if (dateStr.equals("9999-99-99")) {
                            // When adding offsets for time only vars, it messes up the default 9999-99-99 format so we need to reformat it that way
                            formatter = DateTimeFormatter.ofPattern("'9999-99-99T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        } else {
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX")
                            startTime = date.format(formatter)
                        }
                    }
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

private formatHubVariableNameWithTime(hubVariable, startTime) {
    def dtString = ""
    def date = getDateFromDateTimeString(startTime)
    if (!date.equals("9999-99-99")) {
        dtString += "$date"
    }
    def time = getTimeFromDateTimeString(startTime)
    if (!time.equals("99:99")) {
        if (dtString.length() > 0) {
            dtString += "<br>"
        }
        dtString += time
    }
    return "$hubVariable<br>($dtString)"
}

private getTimeFromDateTimeString(dt) {
    return dt.substring(11, dt.length() - 12)
}

private getDateFromDateTimeString(dt) {
    return dt.tokenize("T")[0]
}

// Formating function for consistency and ease of changing style
def getFormat(type, myText = "") {
    if (type == "title") return "<h3 style='color:#2196F3; font-weight: bold; margin-bottom: 15px;'>${myText}</h3>"
    if (type == "blueRegular") return "<div style='color:#2196F3; font-weight: bold; font-size: 16px; padding: 5px 0;'>${myText}</div>"
    if (type == "noticable") return "<div style='color:#FF5722; font-weight: 500; padding: 3px 0;'>${myText}</div>"
    if (type == "important") return "<div style='color:#00BCD4; font-weight: 500; padding: 3px 0;'>${myText}</div>"
    if (type == "lessImportant") return "<div style='color:#4CAF50; font-weight: normal; padding: 3px 0;'>${myText}</div>"
    if (type == "header") return "<div style='color:#212121; font-weight: bold; font-size: 16px; margin-top: 15px; margin-bottom: 5px;'>${myText}</div>"
    if (type == "important2") return "<div style='color:#424242; font-weight: normal;'>${myText}</div>"
}

// Helper to generate & format a button
String buttonLink(String btnName, String linkText, color = "#2196F3", font = "15px") {
    def (action, deviceId, scheduleId) = btnName.tokenize('|')
    if (["editStartTime", "newOffset", "desiredLevel", "selectVariableStartTime"].contains(action)) {
        return """<button type="button" name="${btnName}" id="${btnName}" title="Button" onClick='${action}Popup("${deviceId}", "${scheduleId}", "${linkText}")' style='color:$color;cursor:pointer;font-size:$font;font-weight:500;padding:2px 4px;border-radius:4px;transition:all 0.3s ease;display:inline-block;background:none;border:none'>${linkText}</button>"""
    }
    return """<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font;font-weight:500;padding:2px 4px;border-radius:4px;transition:all 0.3s ease;display:inline-block'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"""
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
    if (!state.accessToken) {
        createAccessToken()
    }
}

def installed() {  // only runs once for new app 'Done' or first time open
    logDebug "installed called"
    updated()
}
