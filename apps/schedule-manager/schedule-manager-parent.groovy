/**
 * ==========================  Schedule Manager (Parent App) ==========================
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
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
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
 *  Last modified: 2024-02-19
 *
 *  Changelog:
 *
 *  1.0 - Initial release
 *
 */

definition(
        name: "Schedule Manager",
        namespace: "evcallia",
        author: "Evan Callia",
        singleInstance: true,
        installOnOpen: true,
        description: "Configure schedules for devices - Parent",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: ""
)

preferences {
    page(name: 'mainPage', title: 'Schedule Manager - App', install: true, uninstall: true) {
        section {
            app(name: "childApps", appName: "Schedule Manager (Child App)", namespace: "evcallia",
                    title: "Add New Schedule", multiple: true)
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    log.debug "initialize()"
    childApps.each {child ->
        log.debug "child app: ${child.label}"
    }
}