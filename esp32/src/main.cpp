#include <Arduino.h>
#include <BluetoothSerial.h>
#include <TFT_eSPI.h>
#include "Protocol.h"

static const uint16_t COLOR_BACKGROUND = TFT_BLACK;
static const uint16_t COLOR_CYAN = 0x3F5D;   // aprox. #3FE0E0 em RGB565
static const uint16_t COLOR_AMBER = 0xFD26;  // aprox. #FFA733 em RGB565
static const uint16_t COLOR_YELLOW = 0xF611; // aprox. #F2C230
static const uint16_t COLOR_ORANGE = 0xF421; // aprox. #F28C30
static const uint16_t COLOR_RED = 0xE8C7;    // aprox. #E23B3B

static const unsigned long BT_TIMEOUT_MILLIS = 5000;

BluetoothSerial SerialBT;
TFT_eSPI tft = TFT_eSPI();

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
        String limite = "Limite " + String(lastMessage.maxSpeedKmh) + " km/h";
        tft.drawString(limite, tft.width() / 2, tft.height() - 20, 4);
    }
}

void drawBluetoothDisconnected(bool iconVisible) {
    tft.fillScreen(COLOR_BACKGROUND);
    tft.setTextDatum(MC_DATUM);
    if (iconVisible) {
        tft.setTextColor(COLOR_RED, COLOR_BACKGROUND);
        tft.drawString("BT", tft.width() / 2, tft.height() / 2 - 20, 7);
    }
    tft.setTextColor(TFT_WHITE, COLOR_BACKGROUND);
    tft.drawString("reconectando...", tft.width() / 2, tft.height() / 2 + 30, 4);
}

void setup() {
    Serial.begin(115200);
    SerialBT.begin("RadarAlert-ESP32");

    tft.init();
    tft.setRotation(1); // paisagem, 320x170
    drawBluetoothDisconnected(true);
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
        drawBluetoothDisconnected(iconVisible);
        delay(150);
    }
}
