# Adding a New Radiation Portal Monitor System

This document provides the correct process for adding a new radiation portal monitor system using the Lane Systems sensor.

## Step 1: Add a New Module
1. Navigate to the admin panel at [https://localhost/sensorhub/admin](https://localhost/sensorhub/admin) (or your configured domain).
2. Right-click in the **Sensors** area.
3. Click **Add New Module**.
4. Select the **Lane Systems** module.

## Step 2: Configure Location and Manufacturer
1. Enter the required **Location Information**.
2. Go to the tab that allows you to enter the radiation portal monitor manufacturer (Rapiscan or Aspect).
3. If you select **Rapiscan**, enter the **IP address** and the **communication port**.

## Step 3: Add Camera Systems
Add the camera system of your choice by selecting the appropriate option: **Sony**, **Axis**, or **Generic**.

### Sony or Axis Cameras
- If a Sony or Axis camera is selected, you will be presented with options to enter the **username**, **password**, and **IP address** for the camera.
- **Sony** cameras will additionally have the ability to choose between the **MJPG** and **H.264** video streams.

### Generic Cameras
- If a Generic camera is selected, you will have the option to enter the **username**, **password**, **IP address**, **port number**, and the **stream URL**.

## Step 4: Save Changes
**Important: The save buttons must be clicked in the specific order described below.**

1. **Session Save:** After all information has been entered, save your changes by clicking the apply button on the **right side** of the upper corner of the screen.
2. **Persistent Save:** Next, save the changes by clicking the save icon on the **left side** of the screen. This ensures the configuration is written to the container's persistent volume.

## Step 5: Start the Module
1. Right-click on the newly created module in the Sensors area and select **Start**.
2. After initialization, the module will start.

## Step 6: Verify in OSCAR Viewer
Once the module has been successfully added and started:
1. Switch to the Oscar Viewer Dashboard at [https://localhost/](https://localhost/).
2. You will often need to **refresh this page** to make the newly added sensors appear.
