package pl.touk.nussknacker.engine.aws

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesisanalyticsv2.model.{S3ContentLocation, S3ContentLocationUpdate}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, S3Exception}

import java.nio.file.{Files, Path}

class S3Client(
    bucketName: String,
    region: Region,
    accessKeyId: String,
    secretAccessKey: String
) extends AutoCloseable
    with LazyLogging {

  private lazy val s3Client = S3AsyncClient
    .builder()
    .region(region)
    .credentialsProvider(
      StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
    )
    .build()

  def upload(jar: Path): IO[S3ObjectLocation] = {
    val fileName = jar.getFileName.toString
    val body     = AsyncRequestBody.fromFile(jar)
    IO.blocking(Files.size(jar)).flatMap { size =>
      upload(fileName, body, size)
    }
  }

  def upload(fileName: String, bytes: Array[Byte]): IO[S3ObjectLocation] = {
    val body = AsyncRequestBody.fromBytes(bytes)
    val size = bytes.length.toLong
    upload(fileName, body, size)
  }

  private def upload(fileName: String, body: AsyncRequestBody, size: Long): IO[S3ObjectLocation] = {
    val putRequest = PutObjectRequest
      .builder()
      .key(fileName)
      .bucket(bucketName)
      .ifNoneMatch("*")
      .contentLength(size)
      .build()
    val location = S3ObjectLocation(bucketName, fileName)
    IO.delay(logger.debug("Attempting to upload '{}' to S3 bucket '{}'", fileName, bucketName)) *>
      IO.fromCompletableFuture(IO.delay(s3Client.putObject(putRequest, body)))
        .map { _ =>
          logger.debug("Successfully uploaded: '{}'", location.uri)
        }
        .recover {
          case e: S3Exception if e.statusCode() == 412 =>
            logger.debug(
              "AWS returned HTTP status code 412 for '{}' upload. " +
                "This is the standard response when object with same hash was already uploaded.",
              fileName
            )
            ()
        }
        .as(location)
  }

  override def close(): Unit = s3Client.close()
}

final case class S3ObjectLocation(bucket: String, fileKey: String) {
  def uri: String = s"s3://$bucket/$fileKey"
  def toFlinkClientS3Location: S3ContentLocation =
    S3ContentLocation.builder().bucketARN(s"arn:aws:s3:::$bucket").fileKey(fileKey).build()
  def toFlinkClientS3LocationUpdate: S3ContentLocationUpdate =
    S3ContentLocationUpdate.builder().bucketARNUpdate(s"arn:aws:s3:::$bucket").fileKeyUpdate(fileKey).build()
}
