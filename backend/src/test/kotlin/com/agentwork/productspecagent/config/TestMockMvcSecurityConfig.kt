package com.agentwork.productspecagent.config

import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder

/**
 * Applies SecurityMockMvcConfigurers.springSecurity() to every MockMvc instance
 * created by @AutoConfigureMockMvc so that @WithMockUser works correctly with the
 * SecurityContextHolderFilter in Spring Security 6+.
 */
@Configuration
class TestMockMvcSecurityConfig {

    @Bean
    fun securityMockMvcBuilderCustomizer(): MockMvcBuilderCustomizer =
        MockMvcBuilderCustomizer { builder: ConfigurableMockMvcBuilder<*> ->
            builder.apply(SecurityMockMvcConfigurers.springSecurity())
        }
}
