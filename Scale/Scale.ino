/**
 * @file SmartScale.ino
 * @brief Akıllı Mutfak Tartısı. Wi-Fi ile ağa bağlanır, web sunucusu sağlar ve HX711 sensörü aracılığıyla yük hücresi üzerinden ağırlık ölçümü yapar.
 * Wi-Fi üzerinden tartının verilerini paylaşabilir.
 */


#include <WiFi.h>
#include <WebServer.h>
#include <TFT_eSPI.h>    // Ekran kütüphanesi
#include <HX711.h>       // HX711 load cell kütüphanesi
#include <ESPmDNS.h>     // mDNS desteği için
#include "OpenSans28.h"  // Türkçe karakterler için özel font
#include "OpenSans42.h"  // Türkçe karakterler için özel font
#include <LittleFS.h>


/**
 * @brief HX711 ve diğer bileşenler için kullanılan pin tanımları.
 */
#define LOADCELL_DOUT_PIN 14   // HX711 DT pini
#define LOADCELL_SCK_PIN 13    // HX711 SCK pini
#define BUTTON_TARE_PIN 25     // Dara (tare) butonu
#define BUTTON_SETUP_PIN 26    // Kurulum (setup) butonu
#define SCREEN_CONTROL_PIN 15  // Ekran kontrol pini

/**
 * @brief Projede kullanılan fontların tanımlanması.
 */
#define OPEN_SANS OpenSans28
#define OPEN_SANSB OpenSans42

/**
 * @brief Load cell sensörünü kontrol eden HX711 nesnesi.
 */
HX711 scale;


/**
 * @brief TFT eSPI ekran nesnesi.
 */
TFT_eSPI tft = TFT_eSPI();


/**
 * @brief Yük hücresinin kalibrasyon faktörü.
 */
float calibration_factor = 199.0;


/**
 * @brief Gram olarak ölçülen ağırlık değeri.
 */
int weight = 0;


/**
 * @brief Ekranın açık/kapalı durumunu tutan bool değişkeni.
 */
bool screenOn = true;


/**
 * @brief Cihazın derin uykudan yeni uyanıp uyanmadığı bilgisini saklıyor.
 */
bool justWokeUp = false;


/**
 * @brief Uyandıktan sonraki zamanı kaydetme.
 */
unsigned long wakeUpTime = 0;


/**
 * @brief Setup (Wi-Fi kurulum) modunun önceki döngüdeki durumu.
 */
bool lastSetupMode = false;


/**
 * @brief Son ölçülen ağırlık değeri (karşılaştırma için).
 */
int lastWeight = -999;


/**
 * @brief Ekranın yeniden güncellenmesi gerektiğini ifade eden bayrak.
 */
bool displayNeedsUpdate = true;


/**
 * @brief Daha önce kaydedilmiş Wi-Fi SSID değeri.
 */
String storedSSID = "";


/**
 * @brief Daha önce kaydedilmiş Wi-Fi şifresi.
 */
String storedPASS = "";


/**
 * @brief Wi-Fi kurulum modunun açık/kapalı durumu.
 */
bool wifiSetupMode = false;


/**
 * @brief Web sunucusu için ku80 numaralı port kullanılıyor.
 */
WebServer server(80);


/**
 * @brief Gelen web isteklerini işleyen fonksiyonlar.
 */
void handleRoot();
void handleTare();
void handleSetCredentials();
void handleValue();
void handleSleep();
void handleScreenOn();
void handleScreenOff();


/**
 * @brief Wi-Fi bilgilerini yükleme/kaydetme ve kontrol fonksiyonları.
 */
void loadCredentials();
void saveCredentials(const String& ssid, const String& pass);
bool credentialsAvailable();


/**
 * @brief Wi-Fi erişim noktası (AP) modunu başlatma
 */
void startAPMode();


/**
 * @brief Wi-Fi ağına bağlan
 */
void connectToWiFi();


/**
 * @brief Wi-Fi bağlantı durumunu göster
 */
void displayWiFiStatus();


/**
 * @brief ESP32'yi derin uyku moduna geçir
 */
void enterDeepSleep();


/**
 * @brief Ekranı açıp kapatma kontrolünü sağlayan fonksiyon.
 *
 * @param state true ise ekran açılır, false ise ekran kapanır.
 */
void controlScreen(bool state);


/**
 * @brief Yeni SSID ve şifre bilgilerini alıp kaydeden web isteğini işler.
 *
 * İstemciden "ssid" ve "pass" parametrelerini bekle. Parametreler doğru ise,
 * bu bilgileri kalıcı hafızaya (LittleFS) kaydedip cihazı yeniden başlat.
 */
void handleSetCredentials() {
  if (server.hasArg("ssid") && server.hasArg("pass")) {
    String newSSID = server.arg("ssid");
    String newPASS = server.arg("pass");
    saveCredentials(newSSID, newPASS);
    server.send(200, "text/plain", "Credentials received. Restarting...");
    delay(1000);
    ESP.restart();
  } else {
    server.send(400, "text/plain", "Missing ssid or pass parameters");
  }
}


/**
 * @brief LittleFS üzerinden kayıtlı Wi-Fi SSID ve şifre bilgilerini yükle.
 */
void loadCredentials() {
  if (!LittleFS.begin(true)) {
    Serial.println("LittleFS mount failed. Formatting...");
    LittleFS.format();
    if (!LittleFS.begin(true)) {
      Serial.println("LittleFS failed after formatting.");
      return;
    }
  }


  if (LittleFS.exists("/credentials.txt")) {
    File f = LittleFS.open("/credentials.txt", "r");
    if (f) {
      storedSSID = f.readStringUntil('\n');
      storedSSID.trim();
      storedPASS = f.readStringUntil('\n');
      storedPASS.trim();
      f.close();
      Serial.println("Loaded credentials:");
      Serial.println("SSID: " + storedSSID);
      Serial.println("PASS: " + storedPASS);
    } else {
      Serial.println("Failed to open credentials file.");
    }
  } else {
    Serial.println("No credentials found in LittleFS.");
  }
}


/**
 * @brief LittleFS üzerinden Wi-Fi SSID ve şifre bilgilerini kaydet.
 *
 * @param ssid Kaydedilecek Wi-Fi SSID değeri.
 * @param pass Kaydedilecek Wi-Fi şifre değeri.
 */
void saveCredentials(const String& ssid, const String& pass) {
  if (!LittleFS.begin(true)) {
    Serial.println("LittleFS mount failed. Cannot save credentials.");
    return;
  }


  File f = LittleFS.open("/credentials.txt", "w");
  if (f) {
    f.println(ssid);
    f.println(pass);
    f.close();
    Serial.println("Credentials saved:");
    Serial.println("SSID: " + ssid);
    Serial.println("PASS: " + pass);
  } else {
    Serial.println("Failed to open credentials file for writing.");
  }


  LittleFS.end();
}

/**
 * @brief Kayıtlı Wi-Fi bilgileri olup olmadığını kontrol et.
 *
 * @return eğer SSID ve şifre mevcutsa true, değilse false.
 */
bool credentialsAvailable() {
  return (storedSSID.length() > 0 && storedPASS.length() > 0);
}

/**
 * @brief Wi-Fi bağlantı durumunu TFT ekranda gösterir.
 *
 * Bağlandı, AP modunda ya da bağlantı yok gibi durumları renk ve yazıyla ifade ederek kullanıcıyı bilgilendirir. Eğer bağlıysa mavi renkte Wi-Fi yazar.
* Setup modunda sarı renkte tartının WiFi şifre bilgileri ekranda gösterilir. Kullanıcı tartının ağına bağlanarak uygulama üzerinden ev ağının bilgilerini tartıya girebilir
 */
void displayWiFiStatus() {
  if (WiFi.status() == WL_CONNECTED) {
    tft.loadFont(OPEN_SANS);
    tft.setTextColor(TFT_BLUE, TFT_BLACK);
    tft.setTextDatum(MC_DATUM);
    tft.drawString("Wi-Fi", 120, 20);
  } else if (wifiSetupMode) {
    tft.setTextColor(TFT_YELLOW, TFT_BLACK);
    tft.loadFont(OPEN_SANS);
    tft.setTextDatum(MC_DATUM);
    tft.drawString("SSID/Şifre", 120, 145);
    tft.drawString("SmartScale", 120, 170);
    tft.drawString("DietApp2024", 120, 195);
  } else {
    tft.setTextColor(TFT_RED, TFT_BLACK);
    tft.loadFont(OPEN_SANS);
    tft.setTextDatum(MC_DATUM);
    tft.drawString("Bağlantı Kesildi", 120, 120);
  }
}


/**
 * @brief Cihazı derin uyku (Deep Sleep) moduna alır.
 *
 * @details Tartının uyanması için TARE ve SETUP butonlarına aynı anda basılması gerekir.
 * Ekranı temizledikten sonra derin uykuya geçer.
 */
void enterDeepSleep() {
  Serial.println("Entering deep sleep...");
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setTextDatum(MC_DATUM);
  tft.drawString("Derin Uyku", 120, 120);


  esp_sleep_enable_ext1_wakeup(
    (1ULL << BUTTON_TARE_PIN) | (1ULL << BUTTON_SETUP_PIN),
    ESP_EXT1_WAKEUP_ALL_LOW);


  delay(1000);
  esp_deep_sleep_start();
}




/**
 * @brief Ana sayfa (root) isteğini karşılayan web sunucu fonksiyonu.
 *
 * Ağırlık bilgisini (weight) bir HTML sayfası olarak geri döndürür.
 */
void handleRoot() {
  server.send(200, "text/html",
              "<!DOCTYPE html>"
              "<html lang='tr'>"
              "<head>"
              "<meta charset='UTF-8'>"
              "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
              "<title>SmartScale</title>"
              "<style>body { font-family: Arial, sans-serif; text-align: center; }</style>"
              "</head>"
              "<body>"
              "<h1>Ağırlık: "
                + String(weight) + " g</h1>"
                                   "</body>"
                                   "</html>");
}


/**
 * @brief Tartıyı sıfırlama (dara) işlemini web üzerinden gerçekleştirir.
 */
void handleTare() {
  scale.tare();
  server.send(200, "text/plain", "Scale zeroed (tared)");
  Serial.println("Scale zeroed via Wi-Fi");
}


/**
 * @brief Ağırlık değerini web üzerinden talep eden isteğe cevap verir.
 *
 * Web istemcisine mevcut ağırlık değerini metin formatında döndürür.
 */
void handleValue() {
  server.send(200, "text/plain", String(weight));
  Serial.println("Weight value sent via Wi-Fi: " + String(weight) + " g");
}


/**
 * @brief Web üzerinden gelen istekle cihazı derin uyku moduna geçirir.
 */
void handleSleep() {
  server.send(200, "text/plain", "Entering deep sleep...");
  Serial.println("Entering deep sleep via Wi-Fi...");
  delay(100);
  enterDeepSleep();
}


/**
 * @brief Ekranı web üzerinden açma.
 */
void handleScreenOn() {
  controlScreen(true);
  server.send(200, "text/plain", "Screen turned ON");
  Serial.println("Screen turned ON via Wi-Fi");
}


/**
 * @brief Ekranı web üzerinden kapatma.
 */
void handleScreenOff() {
  controlScreen(false);
  server.send(200, "text/plain", "Screen turned OFF");
  Serial.println("Screen turned OFF via Wi-Fi");
}


/**
 * @brief Ekranı açma veya kapatma işlemini kontrol eder.
 *
 * @param state true verildiğinde ekran açılır, false verildiğinde kapatılır.
 */
void controlScreen(bool state) {
  if (state) {
    digitalWrite(SCREEN_CONTROL_PIN, HIGH);
    screenOn = true;
    tft.init();
    displayNeedsUpdate = true;
    tft.setRotation(1);
  } else {
    digitalWrite(SCREEN_CONTROL_PIN, LOW);
    screenOn = false;
    tft.fillScreen(TFT_BLACK);
  }
}


/**
 * @brief Wi-Fi AP modunu başlatır. Yeni SSID ve şifreyi almak için kullanılır. Alınan bilgiler kullanılarak daha sonra ev ağına bağlanılacaktır.
 */
void startAPMode() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP("SmartScale", "DietApp2024");
  Serial.println("Started AP mode: SSID=SmartScale, PASS=DietApp2024");
  server.on("/setCredentials", handleSetCredentials);
  server.begin();
}


/**
 * @brief Kayıtlı SSID ve şifre ile Wi-Fi ağına bağlanır.
 *
 * Bağlantı 15 saniye içinde başarısız olursa tekrar başlatılır (ESP.restart) kullanarak.
 */
void connectToWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(storedSSID.c_str(), storedPASS.c_str());
  Serial.print("Connecting to Wi-Fi");
  unsigned long startAttemptTime = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttemptTime < 15000) {
    delay(500);
    Serial.print(".");
  }
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\nFailed to connect to Wi-Fi. Retrying in 10 seconds...");
    delay(10000);
    ESP.restart();
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nConnected to Wi-Fi!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());


    if (MDNS.begin("smartscale")) {
      Serial.println("mDNS responder started: http://smartscale.local");
    } else {
      Serial.println("Error starting mDNS responder. Retrying...");
      delay(1000);
      MDNS.begin("smartscale");
    }


    server.on("/", handleRoot);
    server.on("/tare", handleTare);
    server.on("/value", handleValue);
    server.on("/sleep", handleSleep);
    server.on("/screenOn", handleScreenOn);
    server.on("/screenOff", handleScreenOff);
    server.begin();
    Serial.println("Web server started.");
  } else {
    Serial.println("\nFailed to connect to Wi-Fi.");
  }
}


/**
 * @brief Setup fonksiyonu, mikrokontrolcü başlatıldığında veya resetlendiğinde bir kez çalışmaktadır.
 */
void setup() {
  Serial.begin(115200);


  pinMode(SCREEN_CONTROL_PIN, OUTPUT);
  controlScreen(true);


  pinMode(BUTTON_TARE_PIN, INPUT_PULLUP);
  pinMode(BUTTON_SETUP_PIN, INPUT_PULLUP);


  // Derin uykudan uyanılmış mı kontrol et
  if (esp_sleep_get_wakeup_cause() == ESP_SLEEP_WAKEUP_EXT1) {
    Serial.println("Woke up from deep sleep.");
    justWokeUp = true;
    wakeUpTime = millis();
  }


  // Açılırken setup butonuna basılı tutularak Wi-Fi AP moduna geçilebilir
  if (digitalRead(BUTTON_SETUP_PIN) == LOW) {
    wifiSetupMode = true;
    startAPMode();
    return;
  }


  // Yük hücresi (HX711) ayarları
  scale.begin(LOADCELL_DOUT_PIN, LOADCELL_SCK_PIN);
  scale.set_scale(calibration_factor);
  scale.tare();


  // Ekran ayarları
  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  tft.setTextColor(TFT_WHITE, TFT_BLACK);
  tft.setTextDatum(MC_DATUM);


  // Wi-Fi kimlik bilgilerini yükle
  loadCredentials();


  // Kimlik bilgileri varsa normal Wi-Fi bağlantısına geç
  if (credentialsAvailable()) {
    connectToWiFi();
  } else {
    Serial.println("No credentials found. Enter setup mode to configure.");
  }
}


/**
 * @brief Ana döngü fonksiyonu (loop), sürekli olarak çalışır.
 */
void loop() {
  // Eğer AP modunda veya Wi-Fi bağlıysa web isteklerini dinle
  if (wifiSetupMode || WiFi.status() == WL_CONNECTED) {
    server.handleClient();
  }


  // Uyanma sonrası bir süre geçtikten sonra 'justWokeUp' bayrağını sıfırla
  if (justWokeUp && millis() - wakeUpTime > 5000) {  // 5 saniye bekle
    justWokeUp = false;
  }




  // 10 adet ölçüm alıp ortalamasını almak için parametre: get_units(10)
  weight = int(round(scale.get_units(10)));


  // Ağırlık veya Wi-Fi setup durumu değişmişse ekranda güncelleme gerek
  if (weight != lastWeight || wifiSetupMode != lastSetupMode) {
    displayNeedsUpdate = true;
    lastWeight = weight;
    lastSetupMode = wifiSetupMode;
  }


  // Ekran güncellemeye ihtiyaç duyuyorsa ve ekran açıksa
  if (displayNeedsUpdate && screenOn) {
    tft.fillScreen(TFT_BLACK);  // Ekranı temizle
    tft.loadFont(OPEN_SANS);    // Özel fontu yükle
    tft.setTextColor(TFT_WHITE, TFT_BLACK);
    tft.setTextDatum(MC_DATUM);
    tft.drawString("Ağırlık:", 120, 70);


    String weightText = String(weight) + "g";
    tft.loadFont(OPEN_SANSB);
    tft.drawString(weightText, 120, 120);  // Ağırlık değerini ortada göster


    if (wifiSetupMode) {
      tft.loadFont(OPEN_SANS);
      tft.setTextColor(TFT_YELLOW, TFT_BLACK);
      tft.drawString("SSID/Şifre", 120, 150);
      tft.drawString("SmartScale", 120, 175);
      tft.drawString("DietApp2024", 120, 200);
    } else {
      displayWiFiStatus();
    }

    displayNeedsUpdate = false;
  }

  // Her iki buton da basılıysa derin uykuya geç
  if (!justWokeUp && digitalRead(BUTTON_TARE_PIN) == LOW && digitalRead(BUTTON_SETUP_PIN) == LOW) {
    enterDeepSleep();
  }
  // Sadece TARE butonuna basılırsa dara işlemi yap
  else if (digitalRead(BUTTON_TARE_PIN) == LOW) {
    delay(50);  // Debounce gecikmesi
    if (digitalRead(BUTTON_TARE_PIN) == LOW) {
      scale.tare();
      Serial.println("Scale zeroed via button press");
      displayNeedsUpdate = true;
      while (digitalRead(BUTTON_TARE_PIN) == LOW) {
        delay(10);
      }
    }
  }
  delay(100);
}
