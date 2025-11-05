/**
 * ============================  Laundry Notifications (Parent App) ============================
 *
 *  DESCRIPTION:
 *  The Laundry Notifications parent app manages one or more child apps that monitor laundry
 *  machines and notify the correct person once a cycle has completed.
 *
 *  INSTALLATION:
 *  1. Install (copy/paste) this parent app code into Hubitat.
 *  2. Install the accompanying child app code (Laundry Notifications - Child).
 *  3. Create an instance of this parent app within Hubitat, then add as many child app
 *     instances as needed for your appliances.
 *
 *  NOTE:
 *  All configuration for hub variables, sensors, timers, and notifications is handled inside
 *  each child app instance. The parent acts as a simple container and facilitator for the
 *  child apps.
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
 *  Last modified: 2025-11-04
 */

definition(
        name: "Laundry Notifications",
        namespace: "evcallia",
        author: "Evan Callia",
        singleInstance: true,
        installOnOpen: true,
        description: "Parent container for Laundry Notifications child apps.",
        category: "Convenience",
        iconUrl: "",
        iconX2Url: "",
        iconX3Url: "",
)

preferences {
    page(name: 'mainPage', title: 'Laundry Notifications - Parent', install: true, uninstall: true) {
        section('Instructions') {
            paragraph 'Add child app instances for each laundry appliance you want to monitor.'
        }
        section('Laundry Notification Apps') {
            app(name: 'childApps', appName: 'Laundry Notifications (Child App)', namespace: 'evcallia',
                    title: 'Add New Laundry Monitor', multiple: true)
        }
    }
}

void installed() {
    log.debug 'Laundry Notifications parent installed'
    initialize()
}

void updated() {
    log.debug 'Laundry Notifications parent updated'
    initialize()
}

void initialize() {
    log.debug "Parent app managing ${childApps?.size() ?: 0} child app(s)."
    childApps?.each { child ->
        log.debug "  Child app: ${child.label}"
    }
}
