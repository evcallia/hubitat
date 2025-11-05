# hubitat
Repo for any custom Hubitat apps, drivers, etc.

Feel free to use anything in this repo, but note that I take no responsibility/liability for anything you choose to use.

I have tested using my own hub but make no guarantees it will run smoothly.
Feel free to request features and I may or may not get to them. You're always welcome to create a pull request.

## Available Apps

### Laundry Notifications
- Parent/child app that monitors a laundry machine's vibration sensor.
- Reads a hub variable (String) that stores the name of the person to notify.
- Sends configurable notifications to the device mapped to that name and resets the hub variable back to the string value `null` 30 minutes after the cycle completes (unless a new cycle restarts first). Optionally toggles a switch while the cycle is running.
- Configuration, installation, and usage details are documented in [`apps/laundry-notifications/README.md`](apps/laundry-notifications/README.md).
