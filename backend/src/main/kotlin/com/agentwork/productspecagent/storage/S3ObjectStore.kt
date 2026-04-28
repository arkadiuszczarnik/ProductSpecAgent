package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.config.S3StorageProperties
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

@Service
class S3ObjectStore(
    private val s3: S3Client,
    private val props: S3StorageProperties,
) : ObjectStore {

    override fun put(key: String, bytes: ByteArray, contentType: String?) {
        val req = PutObjectRequest.builder()
            .bucket(props.bucket)
            .key(key)
            .apply { if (contentType != null) contentType(contentType) }
            .build()
        s3.putObject(req, RequestBody.fromBytes(bytes))
    }

    override fun get(key: String): ByteArray? = try {
        val req = GetObjectRequest.builder().bucket(props.bucket).key(key).build()
        val resp: ResponseBytes<GetObjectResponse> =
            s3.getObject(req, ResponseTransformer.toBytes())
        resp.asByteArray()
    } catch (_: NoSuchKeyException) {
        null
    }

    override fun exists(key: String): Boolean = try {
        s3.headObject(HeadObjectRequest.builder().bucket(props.bucket).key(key).build())
        true
    } catch (_: NoSuchKeyException) {
        false
    } catch (e: S3Exception) {
        if (e.statusCode() == 404) false else throw e
    }

    override fun delete(key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket).key(key).build())
    }

    override fun deletePrefix(prefix: String) {
        val keys = listKeys(prefix)
        if (keys.isEmpty()) return
        keys.chunked(1000).forEach { batch ->
            s3.deleteObjects { req ->
                req.bucket(props.bucket).delete { d ->
                    d.objects(batch.map { ObjectIdentifier.builder().key(it).build() })
                }
            }
        }
    }

    override fun listKeys(prefix: String): List<String> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { it.key() }
            .toList()

    override fun listEntries(prefix: String): List<ObjectStore.ObjectEntry> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix) }
            .stream()
            .flatMap { it.contents().stream() }
            .map { ObjectStore.ObjectEntry(it.key(), it.lastModified()) }
            .toList()

    override fun listCommonPrefixes(prefix: String, delimiter: String): List<String> =
        s3.listObjectsV2Paginator { it.bucket(props.bucket).prefix(prefix).delimiter(delimiter) }
            .stream()
            .flatMap { it.commonPrefixes().stream() }
            .map { it.prefix().removePrefix(prefix).removeSuffix(delimiter) }
            .toList()
}
