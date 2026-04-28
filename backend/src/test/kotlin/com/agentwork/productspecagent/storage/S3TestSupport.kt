package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.config.S3StorageProperties
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.net.URI

@Testcontainers
abstract class S3TestSupport {

    companion object {
        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .withUserName("testuser")
            .withPassword("testpassword")

        @JvmStatic
        protected lateinit var s3: S3Client

        protected const val BUCKET = "test-bucket"

        @BeforeAll
        @JvmStatic
        fun initS3() {
            s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.s3URL))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.userName, minio.password)
                    )
                )
                .build()
            try {
                s3.createBucket { it.bucket(BUCKET) }
            } catch (_: software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException) {
                // ignore
            }
        }
    }

    @BeforeEach
    fun clearBucket() {
        val keys = s3.listObjectsV2Paginator { it.bucket(BUCKET) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { it.key() }
            .toList()
        keys.chunked(1000).forEach { batch ->
            s3.deleteObjects { req ->
                req.bucket(BUCKET).delete { d ->
                    d.objects(batch.map { ObjectIdentifier.builder().key(it).build() })
                }
            }
        }
    }

    /** Frische S3ObjectStore-Instanz pro Test mit dem Test-Bucket. */
    protected fun objectStore(): S3ObjectStore =
        S3ObjectStore(s3, S3StorageProperties(bucket = BUCKET))
}
