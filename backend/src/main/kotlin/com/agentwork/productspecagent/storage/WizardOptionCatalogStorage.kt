package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.WizardOptionCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service

@Service
class WizardOptionCatalogStorage(private val objectStore: ObjectStore) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): WizardOptionCatalog? {
        val bytes = objectStore.get(KEY) ?: return null
        return json.decodeFromString<WizardOptionCatalog>(bytes.toString(Charsets.UTF_8))
    }

    fun save(catalog: WizardOptionCatalog): WizardOptionCatalog {
        objectStore.put(KEY, json.encodeToString(catalog).toByteArray(), "application/json")
        return catalog
    }

    fun delete() {
        objectStore.delete(KEY)
    }

    companion object {
        const val KEY = "config/wizard-options/catalog.json"
    }
}
