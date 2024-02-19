/**
 * ==========================  Automatic Timer (Parent App) ==========================
 *
 *  DESCRIPTION:
 *  This app is designed to be used as a timer to automatically turn on/off a switch after a configurable
 *  amount of time. Optionally, you may configure an override switch which prevents the timer from running.
 *  In addition to controlling the main switch, this app will also manage state of the override switch if
 *  desired.
 *
 *  Feature:
 *  - Automatically turn off "main" switch after set amount of time
 *  - [Optional] Configure "override switch" that will prevent timer from running
 *  - [Optional] Set the "override switch" back to default when the "main" switch changes to default state (defined as
 *      the opposite of the "mainOnOffSetting")
 *  - [Optional] Set the "override switch" back to default state (defined as the opposite of the "overrideOnOffSetting")
 *      after set amount of time
 *
 *  TO INSTALL:
 *  Add code for parent app (this) and then and child app. Install/create new instance of parent
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
 *  Last modified: 2024-02-19
 *
 *  Changelog:
 *
 *  1.0 - Initial release
 *
 */

definition(
        name: "Automatic Timer",
        namespace: "evcallia",
        author: "Evan Callia",
        singleInstance: true,
        installOnOpen: true,
        description: "Automatically turn switch on/off after set amount of time. Includes various configurations for " +
                "override switches which prevent timer from running.",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: "",
)

preferences {
    page(name: 'mainPage', title: 'Automatic Timer - App', install: true, uninstall: true) {
        section ("Automatic Timers") {
            paragraph "Configurations for a single switch (and optionally an override)."
        }
        section {
            app(name: "childApps", appName: "Automatic Timer (Child App)", namespace: "evcallia",
                    title: "Add New Automatic Timer", multiple: true)
        }
    }
}

void installed() {
    log.debug "installed()"
    initialize()
}

void updated() {
    log.debug "Updated with settings: ${settings}"
    initialize()
}

void initialize() {
    log.debug "initialize()"
    childApps.each {child ->
        log.debug "  child app: ${child.label}"
    }
}
