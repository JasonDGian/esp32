This version includes:
- wifi connection configuration.
- Certificate checks and copy mechanisms with working timestamp comparison.
- calls server for task and receives latest simplified event.

```cpp
// WiFi library for connecting to wireless networks
#include <WiFi.h>
// Library for handling secure HTTPS connections
#include <WiFiClientSecure.h>
// HTTP client library for making HTTP requests
#include <HTTPClient.h>
// Library for serial communication over RS232
#include <HardwareSerial.h>
// SD card library for file storage
#include <SD.h>
// Lightweight flash file system (for internal storage)
#include <LittleFS.h>
// File system library for handling directories
#include <FS.h>
// ESP32 system functions
#include "esp_system.h"
// NTP library to sync time.
#include <time.h>




// ---------------------------------------------
// WiFi Configuration
// ---------------------------------------------
#define WifiSSID "Lobos-2.4G"
#define WifiPassword "UL6ACmnV"

// ---------------------------------------------
// Timezone Configuration
// ---------------------------------------------
#define TZ_INFO "CET-1CEST,M3.5.0/02,M10.5.0/03"

// ---------------------------------------------
// HTTP Server Configuration
// ---------------------------------------------
#define serverTasksAddress "http://192.168.1.100:8085/server-events"

// ---------------------------------------------
// RS232 Serial Port Configuration
// ---------------------------------------------
#define rxSR232Port 16  // RX pin for RS232 communication
#define txSR232Port 17  // TX pin for RS232 communication

// ---------------------------------------------
// SD Card Pin Configuration
// ---------------------------------------------
#define sdCardMOSI 23
#define sdCardMISO 19
#define sdCardClock 18
#define sdCardChipSelect 5

// ---------------------------------------------
// LED Status Indicators
// ---------------------------------------------
#define offlinePin 4   // Indicates offline status
#define onlinePin 0    // Indicates online status
#define littleFSLed 2  // LED for internal storage (LittleFS) status
#define sdFSLed 15     // LED for SD card status

// ---------------------------------------------
// Debugging Configuration
// ---------------------------------------------
#define DEBUG 1

#if DEBUG == 1
#define debug(x) Serial.print(x)
#define debugln(x) Serial.println(x)
#else
#define debug(x)
#define debugln(x)
#endif

// ---------------------------------------------
// SSL Certificate & Device Information
// ---------------------------------------------
// WiFi secure client
WiFiClientSecure client;

// Projector details
String projectorModel = "Epson EB-S41";
String projectorClassroom = "0.01";
String projectorModelQuery = "Epson%20EB-S41";
String projectorClassroomQuery = "0.01";
// Complete url used for GET requests
String taskQueryAddress = "http://192.168.1.100:8085/server-events?projectorModel=" + projectorModelQuery + "&projectorClassroom=" + projectorClassroomQuery; 

// File paths for SSL certificate storage
String sdCertFilePath = "/test.txt";        // SSL certificate stored on SD card
String littleFSCertFilePath = "/test.txt";  // Copy of SSL certificate stored in internal flash (LittleFS)
String metadataFile = "/metadata.txt";

// File system status flags
bool localCertificateExists = false;
bool cardIsMounted = false;
bool sdCertificateExists = false;

// File objects for handling certificates
File localCertFile;
File sdCertFile;

// Real Time clock object to configure device date-time.
//ESP32Time rtc;

// ---------------------------------------------
// Setup Function (Runs Once at Startup)
// ---------------------------------------------
void setup() {
  // -------------------------------
  // Initialize Serial Monitor
  // -------------------------------
  Serial.begin(115200);
  while (!Serial) { ; }  // Wait for Serial to be ready
  debugln("INFO: Serial monitor connected.");

  // -------------------------------
  // Initialize LED Status Indicators
  // -------------------------------
  pinMode(offlinePin, OUTPUT);
  pinMode(onlinePin, OUTPUT);
  pinMode(littleFSLed, OUTPUT);
  pinMode(sdFSLed, OUTPUT);

  // Set default LED states
  digitalWrite(offlinePin, LOW);
  digitalWrite(onlinePin, LOW);
  digitalWrite(littleFSLed, LOW);
  digitalWrite(sdFSLed, LOW);

  // -------------------------------
  // Connect to WiFi
  // -------------------------------
  debug("Connecting to WiFi...");
  WiFi.begin(WifiSSID, WifiPassword);

  // Blink the offline LED while connecting
  while (WiFi.status() != WL_CONNECTED) {
    digitalWrite(offlinePin, HIGH);
    debug(".");
    delay(250);
    digitalWrite(offlinePin, LOW);
    delay(250);
  }

  // WiFi connected, update status LED
  digitalWrite(offlinePin, LOW);
  digitalWrite(onlinePin, HIGH);
  debugln("\nINFO: WiFi connected.");
  debug("IP Address: ");
  debugln(WiFi.localIP());

  // -------------------------------
  // Configure device RTC block
  // This block needs online status.
  // -------------------------------
  setupTime();

  // -------------------------------
  // Initialize Internal File System (LittleFS)
  // -------------------------------
  if (!LittleFS.begin()) {
    debugln("ERROR: Failed to mount LittleFS. Attempting format...");
    if (!LittleFS.begin(true)) {
      debugln("ERROR: LittleFS formatting failed.");
    } else {
      debugln("WARNING: LittleFS formatted successfully.");
    }
  } else {
    debugln("INFO: LittleFS mounted successfully.");
    digitalWrite(littleFSLed, HIGH);  // Turn on LittleFSLed
  }

  // Check if SSL certificate exists in internal storage
  localCertificateExists = LittleFS.exists(littleFSCertFilePath);
  debugln(localCertificateExists ? "INFO: Certificate found in LittleFS." : "WARNING: Certificate NOT FOUND in LittleFS.");

  // -------------------------------
  // Initialize SD Card
  // -------------------------------
  if (!SD.begin(sdCardChipSelect)) {
    debugln("WARNING: No SD card detected.");
    sdCertificateExists = false;
  } else {
    debugln("INFO: SD Card mounted successfully.");

    // Verify SD accessibility by opening root directory
    File testFile = SD.open("/");
    if (!testFile) {
      debugln("ERROR: SD Card not accessible.");
      sdCertificateExists = false;  // Mark SD as inaccessible
    } else {
      testFile.close();
      debugln("INFO: SD Card is accessible.");

      // Check if SSL certificate exists on SD card
      sdCertificateExists = SD.exists(sdCertFilePath);
      debugln(sdCertificateExists ? "INFO: Certificate found in SD card." : "WARNING: Certificate NOT FOUND in SD card.");

      // Turn on SD card LED indicator
      digitalWrite(sdFSLed, HIGH);
      delay(1000);
    }
  }


  // ------------------------------------------------------
  // CERTIFICATE PRESENCE CHECK & SYNCHRONIZATION
  // ------------------------------------------------------
  // This section ensures that the SSL certificate is correctly stored in the device.
  // - If both the local (LittleFS) and SD card certificates exist, it compares timestamps
  //   and updates the local copy if the SD card has a newer version.
  // - If only the SD certificate exists, it copies it to the local filesystem.
  // - If no certificates are found, a warning is displayed.
  // ------------------------------------------------------

  if (localCertificateExists) {
    // CASE 1: Local certificate exists, now checking SD card.
    if (sdCertificateExists) {
      debugln("INFO: Comparing certificate timestamps...");

      // Open both the local and SD certificates to compare their modification timestamps.
      sdCertFile = SD.open(sdCertFilePath, "r");

      if (!sdCertFile) {
        debugln("ERROR: Failed to open SD certificate file.");
        sdCertFile.close();  // Close the local file if SD file failed to open.
      }

      // Get modification timestamps for both files.
      unsigned long localCertModStamp = readTimestampFromMetadataFile();

      unsigned long sdCertModStamp = sdCertFile.getLastWrite();

      // Print Local certificate last modification time
      Serial.print("Local Certificate Last Modified: ");
      printTimestampAsDate(localCertModStamp);

      // Print SD certificate last modification time
      Serial.print("SD Certificate Last Modified: ");
      printTimestampAsDate(sdCertModStamp);

      // Close file after reading timestamps.
      sdCertFile.close();

      // CASE 1A: SD certificate is newer -> Overwrite local copy
      if (sdCertModStamp > localCertModStamp) {
        debugln("INFO: SD Certificate is more recent. Overwriting local certificate.");
        copySDCertificateToLocalFS();
      }
      // CASE 1B: Local certificate is already the latest -> No update needed
      else if (sdCertModStamp < localCertModStamp) {
        debugln("INFO: Local certificate is more recent. No changes applied.");
      }
      // CASE 1C: Both certificates have the same timestamp -> No action needed
      else {
        debugln("INFO: Local certificate is up to date. No changes applied.");
      }

      // Unmount the SD card and turn off its indicator LED after processing.
      SD.end();
      delay(2000);
      digitalWrite(sdFSLed, LOW);
    }
  }
  // CASE 2: Local certificate does not exist -> Copy from SD if available
  else {
    if (sdCertificateExists) {
      debugln("INFO: No local certificate found. Copying from SD...");
      copySDCertificateToLocalFS();
    }
    // CASE 3: No certificates available in either location -> Warning
    else {
      debugln("WARNING: No certificate found in local storage nor SD card.");
    }
  }
  // -------------------------------
  // End of Setup
  // -------------------------------
}

// ---------------------------------------------
// Loop Function (Runs Continuously)
// ---------------------------------------------
void loop() {
  // Listen for serial input to trigger a reboot
  /*
  if (Serial.available()) {
    char received = Serial.read();
    if (received == 'r') {
      Serial.println("Rebooting now...");
      esp_restart();
    }
    if (received == 'd') {  // User sends 'd' for delete
      Serial.println("Attempting to delete certificate...");

      // Delete from LittleFS (Internal Flash)
      if (LittleFS.exists(littleFSCertFilePath)) {
        if (LittleFS.remove(littleFSCertFilePath)) {
          Serial.println("INFO: Certificate deleted from LittleFS.");
        } else {
          Serial.println("ERROR: Failed to delete certificate from LittleFS.");
        }
      } else {
        Serial.println("WARNING: No certificate found in LittleFS.");
      }
      Serial.println("Certificate deletion process completed.");
    }

    if (received == 'o') {
      Serial.println("Attempting to open local certificate...");
      // Delete from LittleFS (Internal Flash)
      if (LittleFS.exists(littleFSCertFilePath)) {

        File file = LittleFS.open(littleFSCertFilePath, "r");

        if (file) {
          Serial.println("INFO: Opening local certificate.");
          while (file.available()) {
            Serial.write(file.read());  // Print each character
          }
          file.close();
        } else {
          Serial.println("ERROR: Failed to open certificate from LittleFS.");
        }
      } else {
        Serial.println("WARNING: No certificate found in LittleFS.");
      }
    }
  }
*/
  callServer();
  delay(30000);
}



// ---------------------------------------------
// Function to sync date and time with ntp server.
// ---------------------------------------------
// This function configures the ESP32's internal RTC with a timezone and synchronizes
// it with an NTP (Network Time Protocol) server. The RTC keeps track of the current
// date and time based on the server’s data.
void setupTime() {
  configTzTime(TZ_INFO, "pool.ntp.org", "time.nist.gov", "time.google.com");  // Configurar NTP
  Serial.println("INFO: Synchronizing with NTP...");

  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    Serial.println("ERROR: Failed to obtain date and time from NTP server.");
    return;
  }

  // Print readable date and time
  char formattedDate[30];
  strftime(formattedDate, sizeof(formattedDate), "%Y-%m-%d %H:%M:%S", &timeinfo);  // Format the time

  Serial.print("INFO: Date and time set successfully: ");
  Serial.println(formattedDate);  // Print formatted date and time
}

// ---------------------------------------------
// Function to print date and time in human readable format.
// ---------------------------------------------
void printTimestampAsDate(unsigned long timestamp) {
  time_t timeStamp = (time_t)timestamp;  // Cast the unsigned long to time_t
  struct tm* timeinfo;
  timeinfo = localtime(&timeStamp);  // Convert timestamp to struct tm

  // Print formatted date & time
  char formattedDate[30];
  strftime(formattedDate, sizeof(formattedDate), "%Y-%m-%d %H:%M:%S", timeinfo);  // Format the time
  Serial.println(formattedDate);                                                  // Print formatted date and time
}


// ---------------------------------------------
// Function to correctly save the currently stored certificate last write date.
// ---------------------------------------------
void writeTimestampToMetadataFile(unsigned long timestamp) {
  // Open metadata file in write mode, which will overwrite existing data
  File metadataFile = LittleFS.open("/metadata.txt", "w");

  if (!metadataFile) {
    Serial.println("ERROR: Failed to open metadata file for writing.");
    return;
  }

  // Write the timestamp to the metadata file
  metadataFile.println(timestamp);

  // Close the metadata file
  metadataFile.close();

  Serial.println("INFO: Certificate timestamp written to metadata file.");
}


// ---------------------------------------------
// Function to correctly read the currently stored certificate last write date.
// ---------------------------------------------
unsigned long readTimestampFromMetadataFile() {
  // Default value for timestamp (0 indicates not found)
  unsigned long timestamp = 0;

  // Open metadata file in read mode
  File metadataFile = LittleFS.open("/metadata.txt", "r");

  if (!metadataFile) {
    Serial.println("ERROR: Failed to open metadata file for reading.");
    return timestamp;
  }

  // Read the timestamp (only one line in the file)
  if (metadataFile.available()) {
    // Read the integer value (timestamp)
    timestamp = metadataFile.parseInt();
  }

  // Close the metadata file
  metadataFile.close();

  // Return the timestamp (0 if not found)
  if (timestamp == 0) {
    Serial.println("WARNING: No timestamp found in metadata.");
  }

  return timestamp;
}


// ---------------------------------------------
// Function to correctly read the currently stored certificate last write date.
// ---------------------------------------------
void copySDCertificateToLocalFS() {
  // Reopen files for reading from SD and writing to LittleFS
  sdCertFile = SD.open(sdCertFilePath, "r");
  if (!sdCertFile) {
    debugln("ERROR: Failed to open SD certificate file for copying.");
  } else {
    localCertFile = LittleFS.open(littleFSCertFilePath, "w");
    if (!localCertFile) {
      debugln("ERROR: Failed to open local certificate file for writing.");
      sdCertFile.close();  // Close SD file if writing fails.
    } else {
      // Copy content from SD certificate to local storage.
      while (sdCertFile.available()) {
        localCertFile.write(sdCertFile.read());
      }

      debugln("INFO: Certificate copied from SD to local storage.");

      // Store the original file's timestamp for future reference.
      unsigned long sdCertTimestamp = sdCertFile.getLastWrite();
      writeTimestampToMetadataFile(sdCertTimestamp);
      debug("Cert file last modification date:");
      printTimestampAsDate(readTimestampFromMetadataFile());

      // Close both files after copying.
      sdCertFile.close();
      localCertFile.close();
    }
  }
}

// ---------------------------------------------
// Function to call the server and retreive server events for this unit.
// ---------------------------------------------
void callServer() {
  debugln("TASK: Inquiring server about tasks.");

  if (WiFi.status() == WL_CONNECTED) {
    WiFiClientSecure client;
    client.setInsecure();  // Allows connection without SSL certificate

    HTTPClient http;

    debug("TASK: Connecting to: ");
    debugln(taskQueryAddress);

    http.begin(taskQueryAddress);
    
    int httpResponseCode = http.GET();

    if (httpResponseCode > 0) {
      debug("TASK: HTTP RESPONSE CODE: ");
      debugln(httpResponseCode);
      String httpResponseData = http.getString();
      debugln("TASK: Response:");
      debugln(httpResponseData);
    } else {
      debug("TASK ERROR: HTTP request failed with code ");
      debugln(httpResponseCode);
      debugln(http.errorToString(httpResponseCode).c_str());
    }

    http.end();
  } else {
    debugln("ERROR: No WiFi connection available.");
  }
}
```
