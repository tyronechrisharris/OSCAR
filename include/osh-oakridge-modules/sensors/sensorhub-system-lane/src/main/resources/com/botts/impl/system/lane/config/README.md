# Lane Sensor System 

Specialized sensor system module to be used to create a lane with an RPM and video drivers. 

## Configuration

Configuring the sensor requires:
Select `Sensors` from the left-hand accordion control and right-click for the context-sensitive menu in the
accordion control.
Click 'Add New Module' and select 'Lane System' from the list of available modules.

**- General Tab:**
  - Module Name: A unique name for the Lane, must be less than 12 characters.
  - UniqueID: The platform's serial number, or a unique identifier. This will be used for all submodules and must be unique.
  - Auto Start: Check the box to start this module when OSH node is launched.
  - Delete Data on Lane Removal: Check the box to remove systems data from database if lane is deleted from node.

**- Fixed Location:**
  - Latitude:
  - Longitude:

**- Lane Options Config:**
- Click `Add` to configure the submodules.
  - *Initial RPM Config:*
    - Click `Add` and select between the `Rapiscan`, `Aspect`, and `RS350` RPMs. To configure the RPMs you need to know the host IP and port of the device. The `Aspect` RPM has an additional configuration.
      - **Rapiscan/Aspect/RS350 RPM**
        - Remote Host: Enter the IP address or hostname of the RPM.
        - Remote Port: Enter the communication port.
      - **Aspect RPM (Specific)**
        - Address Range: Define the search range for Aspect devices.

  - *Initial Camera Config:*
    - Click `Add` and select between the `Axis`, `Sony`, and `Custom` video cameras. To configure the video cameras you need the host IP and port of the device. Additional configurations may be necessary for different camera types.

      - **Sony/Axis/Custom**
        - Remote Host: Enter your camera's `ip.ip.ip.ip:port` (e.g., `192.168.8.77:8554`).
        - Username: Enter your camera's username. This field is treated as a secret.
        - Password: Enter your camera's password. This field is treated as a secret and will not be logged.
      - **Axis (Specific):**
        - Stream Codec: Select the desired codec (H264 or MJPEG).
      - **Custom (Specific):**
        - Stream Path: Enter everything that comes after your camera's `ip.ip.ip.ip:port` (e.g., `/lane04_cam`).

## Internationalization (i18n) and Schemas
Note that machine-readable schemas, including CSV column headers and RPM type identifiers (e.g., `RS350`, `Aspect`), are canonical tokens and must not be translated. Localization should only be applied to UI labels and help text.
