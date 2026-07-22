#include <Arduino.h>
#include <BluetoothSerial.h>
#include <TFT_eSPI.h>
#include <esp_bt_main.h>
#include <esp_bt_device.h>
#include <esp_gap_bt_api.h>
#include "Protocol.h"
#include <LittleFS.h>
#include <TJpg_Decoder.h>


static const uint16_t COLOR_BACKGROUND = TFT_BLACK;
static const uint16_t COLOR_BACKINIT   = 0x1928;   // aprox. #192340 em RGB565
static const uint16_t COLOR_CYAN       = 0x3F5D;   // aprox. #3FE0E0 em RGB565
static const uint16_t COLOR_AMBER      = 0xFD26;  // aprox. #FFA733 em RGB565
static const uint16_t COLOR_YELLOW     = 0xF611; // aprox. #F2C230
static const uint16_t COLOR_ORANGE     = 0xF421; // aprox. #F28C30
static const uint16_t COLOR_RED        = 0xE8C7;    // aprox. #E23B3B 

static const unsigned long BT_TIMEOUT_MILLIS = 5000;

#define FORMAT_LITTLEFS_IF_FAILED true

BluetoothSerial SerialBT;
TFT_eSPI tft = TFT_eSPI();
TFT_eSprite spr = TFT_eSprite(&tft);

RadarMessage lastMessage;
unsigned long lastMessageMillis = 0;
bool everConnected = false;
String lineBuffer;

uint16_t colorForState(const String& state) {
    if (state == "YELLOW") return COLOR_YELLOW;
    if (state == "ORANGE") return COLOR_ORANGE;
    if (state == "RED") return COLOR_RED;
    return COLOR_BACKGROUND;
}

String labelForAlertType(const String& type) {
    if (type == "RADAR") return "SPEED CAMERA";
    if (type == "LOMBADA") return "BUMP";
    if (type == "POLICIA") return "POLICE";
    if (type == "PEDAGIO") return "TOLL";
    return "";
}

bool tft_output(int16_t x, int16_t y, uint16_t w, uint16_t h, uint16_t *bitmap)
{
  if (y >= spr.height())
    return 0;
  spr.pushImage(x, y, w, h, bitmap); // desenha no sprite, nao direto na tela
  return 1;
}

void drawImage(const char *name, int x, int y)
{
  char path[48];
  snprintf(path, sizeof(path), "/icons/%s.jpg", name);
  Serial.printf("[icon] %s\n[x] %d [y] %d\n", path, x, y);
  Serial.printf("[icon] exists=%d\n", LittleFS.exists(path));
  if (LittleFS.exists(path))
  {
    JRESULT rc = TJpgDec.drawFsJpg(x, y, path, LittleFS);
    Serial.printf("[icon] drew %s\n", path);
    Serial.printf("[icon] drawFsJpg rc=%d\n", rc);
    return;
  }


}


void drawIdleOrAlert() {
    bool hasRadar = lastMessage.state != "NONE" && lastMessage.maxSpeedKmh >= 0;
    uint16_t background = hasRadar ? colorForState(lastMessage.state) : COLOR_BACKGROUND;

    tft.fillScreen(background);
    tft.setTextDatum(MC_DATUM);

    tft.setTextColor(COLOR_AMBER, background);
    tft.drawNumber(lastMessage.speedKmh, tft.width() / 2, tft.height() / 2 - 20, 7);

    tft.setTextColor(COLOR_CYAN, background);
    tft.drawString("km/h", tft.width() / 2, tft.height() / 2 + 30, 4);

    if (hasRadar) {
        tft.setTextColor(TFT_WHITE, background);
        String limite = "Limit " + String(lastMessage.maxSpeedKmh) + " km/h";
        tft.drawString(limite, tft.width() / 2, tft.height() - 45, 4);

        String label = labelForAlertType(lastMessage.alertType);
        if (label != "") {
            tft.drawString(label, tft.width() / 2, tft.height() - 20, 2);
        }
    }
}

// Tela de status enquanto não há stream de dados do app. Diferencia a
// primeira espera (nunca conectou) de uma reconexão (já esteve conectado
// e caiu), como um checklist simples de onde o ESP32 está no processo.
void drawStatus(bool iconVisible) {
    spr.fillRect(0, 0, 320, 170, COLOR_BACKINIT);
    spr.setTextDatum(BL_DATUM);
    spr.setTextColor(TFT_WHITE, COLOR_BACKINIT);
    String linha0 = SerialBT.getBtAddressString();
    linha0.trim();
    int16_t tw = spr.textWidth(linha0);
    spr.drawString("RoadAlert-ESP32", 20, 20, 2);
    spr.drawString(linha0, 300 - tw, 20, 2);
    spr.drawLine(0,30,320,30, COLOR_ORANGE);
    spr.setTextDatum(MC_DATUM);
/*      if (iconVisible) {
        spr.setTextColor(COLOR_RED, COLOR_BACKINIT);
        spr.drawString("BT", 20, 20, 2);
    }  */  
    spr.setTextColor(COLOR_YELLOW, COLOR_BACKINIT);
    String linha1 = everConnected ? "Lost Connection" : "Wait For Host";
    spr.drawString(linha1, spr.width() / 2, spr.height() / 2 , 4);
    spr.setTextColor(TFT_WHITE);
    spr.drawString("Reconnecting...", spr.width() / 2, spr.height() - 20, 4);
    spr.pushSprite(0, 0);
}

// Limpa todos os dispositivos pareados salvos na flash do ESP32. Deixado
// comentado depois de resolver um "authentication failed, status:10"
// causado por chave de pareamento desatualizada — só descomentar de novo
// se o mesmo erro voltar a acontecer, já que rodar isso toda vez que liga
// obrigaria a reparear o celular a cada boot.
void clearBondedDevices() {
    int count = esp_bt_gap_get_bond_device_num();
    if (count <= 0) return;

    esp_bd_addr_t devices[count];
    esp_bt_gap_get_bond_device_list(&count, devices);
    for (int i = 0; i < count; i++) {
        esp_bt_gap_remove_bond_device(devices[i]);
    }
}

void setup() {
    Serial.begin(115200);
    SerialBT.begin("RoadAlert-ESP32");
    // clearBondedDevices();
    LittleFS.begin(FORMAT_LITTLEFS_IF_FAILED);

    tft.init();
    tft.setRotation(3); // paisagem, 320x170
    TJpgDec.setJpgScale(1);
    TJpgDec.setSwapBytes(true);
    TJpgDec.setCallback(tft_output);
    spr.createSprite(320, 170);
    drawImage("logoesp", 0, 0);
    spr.pushSprite(0, 0);
    delay(3000);
    drawStatus(true);
}

void loop() {
    bool connected = SerialBT.hasClient();

    while (SerialBT.available()) {
        char c = SerialBT.read();
        if (c == '\n') {
            RadarMessage parsed;
            if (parseRadarMessage(lineBuffer, parsed)) {
                lastMessage = parsed;
                drawIdleOrAlert();
            }
            lastMessageMillis = millis();
            everConnected = true;
            lineBuffer = "";
        } else if (c != '\r') {
            lineBuffer += c;
        }
    }

    bool timedOut = everConnected && (millis() - lastMessageMillis > BT_TIMEOUT_MILLIS);
    if (!connected || timedOut) {
        bool iconVisible = (millis() / 500) % 2 == 0;
        drawStatus(iconVisible);
        delay(250);
    }
}
