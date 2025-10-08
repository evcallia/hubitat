# Laundry Notifications

Laundry Notifications is a parent/child Hubitat app that monitors a laundry appliance's
vibration sensor and notifies the correct person when a cycle finishes. The person to
notify is stored in a hub variable (type: `String`). After the notification is sent, the
hub variable is automatically reset to the string value `null` 30 minutes later unless a
new cycle starts before the timer expires.

## App Structure
- **Parent App:** Acts as a container so you can create one or more child instances.
- **Child App:** Handles all configuration and automation logic for a single laundry
  appliance.

## Installation
1. Import the parent app (`laundry-notifications-parent.groovy`) into your Hubitat hub.
2. Import the child app (`laundry-notifications-child.groovy`).
3. Create a hub variable (type: String) that will hold the name of the person currently
   responsible for the laundry.
4. Install the parent app and add a child instance for each appliance you want to monitor.
5. Configure each child instance with the following inputs:
   - **Instance name:** Provide a friendly label for the child app so you can easily
     identify the appliance it monitors.
   - **Hub variable name:** Select the string hub variable created in step 3 from the
     dropdown list.
   - **Notification mapping:** Select one or more notification-capable devices and provide
     the hub variable value (name) that should trigger each device. Each device can be
     linked to a different name.
   - **Vibration sensor:** Choose the vibration/acceleration sensor attached to the
     appliance.
   - **Minutes of inactivity:** The number of minutes the sensor must remain inactive
     before a cycle is considered complete.
   - **Optional cycle status switch:** Select a switch device to turn on when the machine
     is running and off when the cycle finishes.
   - **Notification message:** Provide custom text for the completion notification. Use
     `%recipient%` to insert the value of the hub variable into the message.

## How It Works
1. When the vibration sensor reports activity, the app assumes the machine is running and
   cancels any pending reset of the hub variable.
2. Once the sensor becomes inactive, the app waits for the configured inactivity period to
   confirm that the cycle has finished.
3. When the cycle completes, the app reads the hub variable to determine which name is set,
   sends a notification to the device mapped to that name, and schedules the hub variable to
   reset back to `null` in 30 minutes. If configured, it also turns off the optional cycle
   status switch.
4. If the machine starts again before the 30-minute reset timer runs, the reset is cancelled
   and a new timer is scheduled after the subsequent cycle completes.

## Tips
- Update the hub variable (using dashboards, rules, or other automations) to the name of the
  person responsible for the next load before the cycle finishes.
- Multiple child instances can point to the same hub variable if desired (for example, one
  for the washer and one for the dryer) as long as each child has its own vibration sensor.
