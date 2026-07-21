package com.radaralert.app.data.radar

/**
 * Faz o parse do CSV interno gerado por tools/convert_radares.py:
 * id,latitude,longitude,velocidade_maxima,sentido_tipo,direcao_graus,tipo,fonte,atualizado_em
 */
object RadarCsvParser {

    fun parse(csvContent: String): List<RadarEntity> {
        val lines = csvContent.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        return lines.drop(1).map { line ->
            val columns = line.split(",")
            RadarEntity(
                id = columns[0].trim().toLong(),
                latitude = columns[1].trim().toDouble(),
                longitude = columns[2].trim().toDouble(),
                velocidadeMaxima = columns[3].trim().toInt(),
                sentidoTipo = columns[4].trim(),
                direcaoGraus = columns[5].trim().toDouble(),
                tipo = columns[6].trim()
            )
        }
    }
}
