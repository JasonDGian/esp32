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

#include "esp_system.h"  // ESP32 system functions

// ---------------------------------------------
// WiFi Configuration
// ---------------------------------------------
#define WifiSSID "¿?"
#define WifiPassword "¿?"

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
#define offlinePin 4     // Indicates offline status
#define onlinePin 0      // Indicates online status
#define littleFSLed 2    // LED for internal storage (LittleFS) status
#define sdFSLed 15       // LED for SD card status

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
String taskQueryAddress = "http://192.168.1.100:8085/server-events"; // Used for GET requests

// File paths for SSL certificate storage
String sdCertFilePath = "/test.txt";        // SSL certificate stored on SD card
String littleFSCertFilePath = "/test.txt";  // Copy of SSL certificate stored in internal flash (LittleFS)

// File system status flags
bool localCertificateExists = false;
bool cardIsMounted = false;
bool sdCertificateExists = false;

// File objects for handling certificates
File localCertFile;
File sdCertFile;

// ---------------------------------------------
// SD Card Initialization Function
// ---------------------------------------------
void sdInitializer() {
  // PLACEHOLDER
}

void copySDCertificateToLocalFS(){

  bool success = true;

      // Open SD certificate file for reading.  
    sdCertFile = SD.open(sdCertFilePath, "r");
    if (!sdCertFile) {  
      debugln("ERROR: Failed to open SD certificate file for copying."); 
      return; 
    }

    // Open a new file in LittleFS for writing.  
    localCertFile = LittleFS.open(littleFSCertFilePath, "w");
    if (!localCertFile) {  
      sdCertFile.close();
      debugln("ERROR: Failed to open local certificate file for writing.");  
      return; 
    }

      // Copy content from SD certificate to local storage (LittleFS).
    while (sdCertFile.available()) {
      char data = sdCertFile.read();  
      if (localCertFile.write(data) == 0) { // Check write success
        debugln("ERROR: Failed to write data to local certificate file.");
        success = false;
        break; // Exit loop on failure
      }
    }

    // Ensure all data is written before closing
    localCertFile.flush();

    if (success){ debugln("INFO: Certificate successfully copied from SD to local storage."); }

    // Close both files after copying.  
    sdCertFile.close();
    localCertFile.close();
}


// ---------------------------------------------
// Setup Function (Runs Once at Startup)
// ---------------------------------------------
void setup() {
  // -------------------------------
  // Initialize Serial Monitor
  // -------------------------------
  Serial.begin(115200);
  while (!Serial) { ; } // Wait for Serial to be ready
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
    digitalWrite(littleFSLed, HIGH); // Turn on LittleFSLed
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
    localCertFile = LittleFS.open(littleFSCertFilePath, "r");
    sdCertFile = SD.open(sdCertFilePath, "r");

    if (!localCertFile) {  
      debugln("ERROR: Failed to open local certificate file.");  
    }

    if (!sdCertFile) {  
      debugln("ERROR: Failed to open SD certificate file.");  
      localCertFile.close();  // Close the local file if SD file failed to open.
    }

    // Get modification timestamps for both files.  
    unsigned long localCertModStamp = localCertFile.getLastWrite();
    debugln("Local certificate timestamp: " + String(localCertModStamp));

    unsigned long sdCertModStamp = sdCertFile.getLastWrite();
    debugln("SD certificate timestamp: " + String(sdCertModStamp));

    // Close files after reading timestamps.  
    localCertFile.close();
    sdCertFile.close();

    // CASE 1A: SD certificate is newer -> Overwrite local copy  
    if (sdCertModStamp > localCertModStamp) {  
      debugln("INFO: SD Certificate is more recent. Overwriting local certificate.");

      copySDCertificateToLocalFS();

      // Close both files after copying.  
      sdCertFile.close();
      localCertFile.close();

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
    digitalWrite(sdFSLed, LOW);
  }
}  
// CASE 2: Local certificate does not exist -> Copy from SD if available  
else {  
  if (sdCertificateExists) {  
    debugln("INFO: No local certificate found. Copying from SD...");

    copySDCertificateToLocalFS();

    // Unmount the SD card and turn off its indicator LED after processing.  
    SD.end();
    digitalWrite(sdFSLed, LOW);
  }  
  // CASE 3: No certificates available in either location -> Warning  
  else {  
    debugln("WARNING: No certificate found in local storage or SD card.");
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
  if (Serial.available()) {
    char received = Serial.read();
    if (received == 'r') {
      Serial.println("Rebooting now...");
      esp_restart();
    }
  }

  // Referencia https://randomnerdtutorials.com/esp32-http-get-post-arduino/#http-get-1

  // Send request ever 30 seconds.
  
  String serverAddress = "http://192.168.1.100:8085/micro-greeting";
  unsigned long lastTime = 0;
  unsigned long timerDelay = 30000;

  if ((millis() - lastTime) > timerDelay) {
  callServer();
  lastTime = millis();
  }
}

void callServer() {
  debugln("Calling server");

  if (WiFi.status() == WL_CONNECTED) {
    debugln("WiFi connected.");

    // Crea el objeto cliente.
    HTTPClient http;

    // Configura la conexión.
    http.begin(serverAddress);

    // Recupera el codigo de respuesta HTTP. 
    int httpResponseCode = http.GET();
    // Recupera la string del cuerpo de la respuesta.
    String httpResponseData = http.getString();

    // Testea el codigo de la respuesta http
    if (httpResponseCode > 0) {

      debug("HTTP RESPONSE CODE:");
      debugln(httpResponseCode);

      // Testea la string del cuerpo de la respuesta.
      if (httpResponseData == "turn-on") {
        delay(5000);
        pinMode(functionPin, OUTPUT);
        debugln(httpResponseData);
        digitalWrite(functionPin, HIGH);
        sr232Port.write();
      }


    } else {
      debugln("HTTP ERROR CODE:");
      debugln(httpResponseCode);
    }

    // Cierra la conexión.
    http.end();

  } else {
    debugln("WiFi NOT connected.");
  }
}


// SCRIPT PARA ENVIO CADA X.
// -----------------------------------------------------------------------------

// the following variables are unsigned longs because the time, measured in
// milliseconds, will quickly become a bigger number than can be stored in an int.
unsigned long lastTime = 0;
// Timer set to 10 minutes (600000)
//unsigned long timerDelay = 600000;
// Set timer to 5 seconds (5000)
unsigned long timerDelay = 30000;

void loop() {

  // Es una función de Arduino que devuelve la cantidad de milisegundos que han transcurrido desde que el programa comenzó a ejecutarse.

  // Es una variable que almacena el momento en que se realizó una determinada operación, como un envío HTTP.

  // Es el intervalo que queremos que pase entre dos operaciones consecutivas (en este caso, 30,000 ms, que son 30 segundos).

   /* (millis() - lastTime) > timerDelay)  
   -> Si desde que se encendió el dispositivo, substraemos el tiempo de la ultima operación realizada y el resultado es mayor al lapso de tiempo que deseamos entre operaciones entonces entrará en ejecucion el bloque de código.

    Si restamos el valor de lastTime (el momento en que se realizó la última operación) del valor actual de millis() (que representa el tiempo total desde que el dispositivo se encendió), obtendremos el tiempo transcurrido desde la última operación.

    La comparación (millis() - lastTime) > timerDelay verifica si este tiempo transcurrido es mayor que el intervalo deseado (timerDelay). Si es así, significa que ya ha pasado suficiente tiempo desde la última ejecución, y el programa procede a realizar la operación correspondiente. Luego, se actualiza el valor de lastTime a millis(), reiniciando así el temporizador para calcular el siguiente intervalo.

    Este mecanismo asegura que las operaciones se ejecuten únicamente después de cumplir el lapso de tiempo establecido. 
   */

  //Send an HTTP POST request every X minutes
  if ((millis() - lastTime) > timerDelay) {
    //Check WiFi connection status
    if(WiFi.status()== WL_CONNECTED){
      HTTPClient http;

      String serverPath = serverName + "?temperature=24.37";
      
      // Your Domain name with URL path or IP address with path
      http.begin(serverPath.c_str());
      
      // If you need Node-RED/server authentication, insert user and password below
      //http.setAuthorization("REPLACE_WITH_SERVER_USERNAME", "REPLACE_WITH_SERVER_PASSWORD");
      
      // Send HTTP GET request
      int httpResponseCode = http.GET();
      
      if (httpResponseCode>0) {
        Serial.print("HTTP Response code: ");
        Serial.println(httpResponseCode);
        String payload = http.getString();
        Serial.println(payload);
      }
      else {
        Serial.print("Error code: ");
        Serial.println(httpResponseCode);
      }
      // Free resources
      http.end();
    }
    else {
      Serial.println("WiFi Disconnected");
    }
    lastTime = millis();
  }
}

```
