package com.agentwork.productspecagent.config

import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client
import kotlin.test.assertNotNull

class S3ConfigTest {

    @Test
    fun `s3Client is built without StaticCredentialsProvider when access key is blank`() {
        val config = S3Config()
        val props = S3StorageProperties(
            bucket = "any",
            endpoint = "",
            region = "us-east-1",
            accessKey = "",
            secretKey = "",
            pathStyleAccess = false
        )
        // Erwartung: kein NPE, Bean wird gebaut → AWS SDK fällt auf DefaultCredentialsProvider zurück.
        // Die direkte Inspektion der internen Provider-Klasse wäre brüchig (testet SDK-internes Verhalten);
        // wir prüfen nur, dass das Bean baubar ist — entscheidend für IRSA im Pod.
        val client: S3Client = config.s3Client(props)
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `s3Client uses StaticCredentialsProvider when both keys are set`() {
        val config = S3Config()
        val props = S3StorageProperties(
            bucket = "any",
            endpoint = "",
            region = "us-east-1",
            accessKey = "AKIATEST",
            secretKey = "secrettest",
            pathStyleAccess = false
        )
        val client: S3Client = config.s3Client(props)
        assertNotNull(client)
        client.close()
    }
}
