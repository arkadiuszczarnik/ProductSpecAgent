package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.storage.AssetBundleStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DefaultAssetBundleBootstrapper(
    private val storage: AssetBundleStorage,
    private val extractor: AssetBundleZipExtractor,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DefaultAssetBundleBootstrapper::class.java)

    override fun run(args: ApplicationArguments) {
        for (resourcePath in defaultBundleResources) {
            seedBundle(resourcePath)
        }
    }

    private fun seedBundle(resourcePath: String) {
        val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?.use { it.readBytes() }
        if (bytes == null) {
            log.warn("Default asset bundle resource '{}' not found; skipping bootstrap", resourcePath)
            return
        }

        val extracted = try {
            extractor.extract(bytes)
        } catch (e: Exception) {
            log.warn("Default asset bundle resource '{}' could not be extracted: {}", resourcePath, e.message)
            return
        }

        try {
            if (storage.findById(extracted.manifest.id) != null) return

            storage.writeBundle(extracted.manifest, extracted.files)
            log.info("Bootstrapped default asset bundle '{}'", extracted.manifest.id)
        } catch (e: Exception) {
            log.warn("Default asset bundle '{}' could not be bootstrapped: {}", extracted.manifest.id, e.message)
        }
    }

    private companion object {
        val defaultBundleResources = listOf(
            "asset-bundles/living-sync-reporter-bundle.zip",
            "asset-bundles/product-spec-sync-bundle.zip",
            "asset-bundles/feature-implementieren-bundle.zip",
        )
    }
}
