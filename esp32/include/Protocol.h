#pragma once
#include <Arduino.h>

// Protocolo de texto simples enviado pelo app Android via Bluetooth SPP:
// "SPEED:78;MAXSPEED:60;DIST:150;STATE:ORANGE;TYPE:LOMBADA\n"
// Um "PING\n" isolado é só o heartbeat de manter-conexão-viva.
// TYPE: RADAR, LOMBADA, POLICIA, PEDAGIO ou NONE (sem alerta ativo).
struct RadarMessage {
    int speedKmh = 0;
    int maxSpeedKmh = -1;
    int distanceMeters = -1;
    String state = "NONE";
    String alertType = "NONE";
};

inline bool parseRadarMessage(const String& line, RadarMessage& out) {
    if (line == "PING") return false;

    int speed = 0;
    int maxSpeed = -1;
    int dist = -1;
    String state = "NONE";
    String alertType = "NONE";

    int start = 0;
    while (start < (int)line.length()) {
        int sep = line.indexOf(';', start);
        String token = (sep == -1) ? line.substring(start) : line.substring(start, sep);

        int colon = token.indexOf(':');
        if (colon != -1) {
            String key = token.substring(0, colon);
            String value = token.substring(colon + 1);
            if (key == "SPEED") speed = value.toInt();
            else if (key == "MAXSPEED") maxSpeed = value.toInt();
            else if (key == "DIST") dist = value.toInt();
            else if (key == "STATE") state = value;
            else if (key == "TYPE") alertType = value;
        }

        if (sep == -1) break;
        start = sep + 1;
    }

    out.speedKmh = speed;
    out.maxSpeedKmh = maxSpeed;
    out.distanceMeters = dist;
    out.state = state;
    out.alertType = alertType;
    return true;
}
