package com.agentwork.productspecagent.config

import com.agentwork.productspecagent.storage.ObjectStore
import com.agentwork.productspecagent.storage.S3ObjectStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3Config {

    @Bean(destroyMethod = "close")
    fun s3Client(props: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(props.region))
            .forcePathStyle(props.pathStyleAccess)

        if (props.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(props.endpoint))
        }
        if (props.accessKey.isNotBlank() && props.secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey)
                )
            )
        }
        return builder.build()
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStore::class)
    fun objectStore(s3Client: S3Client, props: S3StorageProperties): ObjectStore =
        S3ObjectStore(s3Client, props)
}
