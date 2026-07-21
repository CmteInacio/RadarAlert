package com.radaralert.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.radaralert.app.domain.TipoAlerta

/**
 * Guarda quais [TipoAlerta] o usuário quer que disparem alerta (radar fixo,
 * lombada eletrônica, polícia rodoviária, pedágio). Por padrão, todos ativos.
 */
class AlertaTypePrefs(private val prefs: SharedPreferences) {

    fun getEnabledTypes(): Set<TipoAlerta> {
        val saved = prefs.getStringSet(KEY_ENABLED_TYPES, null) ?: return TipoAlerta.entries.toSet()
        val parsed = saved.mapNotNull { name -> runCatching { TipoAlerta.valueOf(name) }.getOrNull() }.toSet()
        return parsed.ifEmpty { TipoAlerta.entries.toSet() }
    }

    fun setEnabledTypes(types: Set<TipoAlerta>) {
        prefs.edit { putStringSet(KEY_ENABLED_TYPES, types.map { it.name }.toSet()) }
    }

    companion object {
        private const val KEY_ENABLED_TYPES = "enabled_alert_types"
    }
}
