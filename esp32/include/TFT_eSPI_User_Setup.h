// Placeholder de configuração do TFT_eSPI para o display Ideaspark 320x170
// (driver ST7789, paisagem). SUBSTITUA pelos pinos reais já usados em
// projetos anteriores com essa mesma placa.

#define ST7789_DRIVER
#define TFT_RGB_ORDER TFT_BGR

#define TFT_WIDTH  170
#define TFT_HEIGHT 320

#define TFT_MOSI 23
#define TFT_SCLK 18
#define TFT_CS   5
#define TFT_DC   2
#define TFT_RST  4
#define TFT_BL   15

#define LOAD_GLCD
#define LOAD_FONT2
#define LOAD_FONT4
#define LOAD_FONT6
#define LOAD_FONT7
#define LOAD_FONT8
#define LOAD_GFXFF
#define SMOOTH_FONT

#define SPI_FREQUENCY 40000000
