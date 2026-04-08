package pl.touk.nussknacker.engine.aws.managedflink

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime
import io.circe
import io.circe.Json
import pl.touk.nussknacker.engine.aws.managedflink.AwsManagedFlinkArgsProvider.nussknackerPropertiesGroupId
import pl.touk.nussknacker.engine.process.runner.FlinkMainArgsProvider
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Using

class AwsManagedFlinkArgsProvider extends FlinkMainArgsProvider {

  override def provideArgs(args: Array[String]): Array[String] = {
    val deploymentPropertiesUri = getDeploymentPropertiesUri()
    val propertiesJsonString    = readFromS3(deploymentPropertiesUri)
    val properties              = decodeJson(propertiesJsonString)

    Array(
      getPropertyUnsafe(properties, "scenario"),
      getPropertyUnsafe(properties, "version"),
      getPropertyUnsafe(properties, "deploymentData"),
      getPropertyUnsafe(properties, "modelConfig")
    )
  }

  private def getDeploymentPropertiesUri(): String = {
    val appProperties = KinesisAnalyticsRuntime.getApplicationProperties()
    Option(appProperties.get(nussknackerPropertiesGroupId))
      .map(_.asScala)
      .flatMap(_.get("deploymentPropertiesLocationUri"))
      .getOrElse {
        throw new IllegalArgumentException(
          s"Missing 'deploymentPropertiesLocationUri' in '$nussknackerPropertiesGroupId' group"
        )
      }
  }

  private def readFromS3(uri: String): String = {
    Using.resource(S3Client.builder().build()) { s3Client =>
      val s3Uri = s3Client.utilities().parseUri(URI.create(uri))

      val bucket = s3Uri
        .bucket()
        .orElseThrow(() => new IllegalArgumentException(s"Missing bucket in URI: $uri"))

      val key = s3Uri
        .key()
        .orElseThrow(() => new IllegalArgumentException(s"Missing key in URI: $uri"))

      val request = GetObjectRequest
        .builder()
        .bucket(bucket)
        .key(key)
        .build()

      val inputStream = s3Client.getObject(request)
      new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    }
  }

  private def decodeJson(json: String): Map[String, String] = {
    circe.parser.decode[Map[String, Json]](json) match {
      case Right(values) =>
        values.view.mapValues(_.spaces2).toMap
      case Left(err) =>
        throw new RuntimeException(s"Failed to decode deployment properties JSON: $err")
    }
  }

  private def getPropertyUnsafe(properties: Map[String, String], key: String): String =
    properties.getOrElse(key, throw new NoSuchElementException(s"Missing '$key' property"))

}

object AwsManagedFlinkArgsProvider {
  val nussknackerPropertiesGroupId = "nussknacker-internal"
}
