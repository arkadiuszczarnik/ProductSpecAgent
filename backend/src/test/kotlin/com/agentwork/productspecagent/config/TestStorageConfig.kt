package com.agentwork.productspecagent.config

import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.ObjectStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Replaces S3ObjectStore with InMemoryObjectStore for all Spring Boot integration tests.
 * This configuration is on the test classpath only, so it is picked up automatically
 * by @SpringBootTest but never by the production JAR.
 */
@Configuration
class TestStorageConfig {

    @Bean
    @Primary
    fun objectStore(): ObjectStore = InMemoryObjectStore()
}
