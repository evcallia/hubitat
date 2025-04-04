# Schedule manager

## Overview
This app allows users to configure a time table, per device, and schedule the desired state for each configured time. 
Users can select any number of switches/dimmers, schedule them based on a set time or sunrise/set (with offset), 
and configure the desired state for that time. Additionally, users can pause the schedule for individual times. 
Advanced options include only running for desired modes or when a specific switch is set in addition to the ability 
to manually pause all schedules.

## Features
- Schedule any number of switches/dimmers
- Schedules based on selected time or sunrise/set with offset
- Individual schedules may be paused
- Set desired state for switch/dimmer to be in at specified time
- [Optional] Configure which modes to run schedules for
- [Optional] Configure "override switch" that will prevent schedules from running
- [Optional] Option to pause all schedules

## Usage
Add code for parent app and then and child app. Install/create new instance of parent app and begin using.

You may also install via Hubitat Package Manager. Search for "Schedule Manager" and follow installation instructions.

### Example
Here we'll walk through the setup of an app
1. Assign a name
2. Select any number of devices to control.
    - They will automatically appear in a table below
    - You must have at least one schedule for a device (you cannot remove all of them)
3. Set up the table of schedules how you'd like it
   - You may click the "Type" of a device to change the capability you're controlling (if there are multiple capabilities)
   - Click the "+" under "Add Run" to add new schedules for a device
   - Configure a static time for a schedule
   - Or configure a device to run at sunrise/set (with offset)
   - Check which days the schedule should apply
   - Enable/pause a schedule
   - Configure the desired state and level of a device
   - You may remove a schedule by clicking the "X" next to a schedule

Advanced Options
1. [Optional] Configure modes for which the schedule should apply
2. [Optional] Configure a switch when must be set for a schedule to be applied 
3. [Optional] Turn "on" devices before issuing "setLevel" command.
   - Useful when a device doesn't turn on via a setLevel command
4. [Optional] Pause all schedule

![example-setup.png](./example-setup.png)

## Anatomy
This app consists of a parent, used essentially for grouping, and a child app which preforms all the logic. 

You man add as many children apps with as many devices as you'd like. I find it helpful to group child apps by similar
schedules rather than just throw everything into a single instance.