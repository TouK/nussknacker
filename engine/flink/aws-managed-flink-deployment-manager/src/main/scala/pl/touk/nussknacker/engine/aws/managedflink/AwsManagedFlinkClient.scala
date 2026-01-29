package pl.touk.nussknacker.engine.aws.managedflink

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.util.ExceptionUtils.unwrapCommonWrappingExceptions
import pl.touk.nussknacker.engine.aws.S3ObjectLocation
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2AsyncClient
import software.amazon.awssdk.services.kinesisanalyticsv2.model._

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class AwsManagedFlinkClient(
    region: Region,
    accessKeyId: String,
    secretAccessKey: String,
    serviceExecutionRoleArn: String,
    logStreamArn: Option[String],
    runtimeEnviornment: RuntimeEnvironment
) extends AutoCloseable
    with LazyLogging {

  private val client = KinesisAnalyticsV2AsyncClient
    .builder()
    .region(region)
    .credentialsProvider(
      StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
    )
    .build()

  def findApplication(name: FlinkApplicationName): IO[Option[ApplicationDetail]] = {
    val request = DescribeApplicationRequest.builder().applicationName(name.value).build()
    IO.fromCompletableFuture(IO.delay(client.describeApplication(request)))
      .map(_.applicationDetail())
      .map(Some(_))
      .recover {
        case NonFatal(e) if unwrapCommonWrappingExceptions(e).isInstanceOf[ResourceNotFoundException] => None
      }
  }

  def updateApplication(
      applicationDetail: ApplicationDetail,
      applicationJarLocation: S3ObjectLocation,
      deploymentPropertiesLocation: S3ObjectLocation,
  ): IO[Unit] = {
    val request = buildUpdateApplicationRequest(applicationDetail, applicationJarLocation, deploymentPropertiesLocation)
    IO.fromCompletableFuture(IO.delay(client.updateApplication(request))).void
  }

  def createApplication(
      name: FlinkApplicationName,
      applicationJarLocation: S3ObjectLocation,
      deploymentPropertiesLocation: S3ObjectLocation,
  ): IO[Unit] = {
    val request = buildCreateApplicationRequest(name, applicationJarLocation, deploymentPropertiesLocation)
    IO.fromCompletableFuture(IO.delay(client.createApplication(request))).void
  }

  def runApplication(
      applicationName: FlinkApplicationName
  ): IO[Unit] = {
    val request = StartApplicationRequest
      .builder()
      .applicationName(applicationName.value)
      .build()
    IO.fromCompletableFuture(IO.delay(client.startApplication(request))).void
  }

  def stopApplication(
      applicationName: FlinkApplicationName
  ): IO[Unit] = {
    val request = StopApplicationRequest
      .builder()
      .applicationName(applicationName.value)
      .build()
    IO.fromCompletableFuture(IO.delay(client.stopApplication(request))).void
  }

  private def buildEnvironmentProperties(
      deploymentPropertiesLocation: S3ObjectLocation
  ): EnvironmentProperties =
    EnvironmentProperties
      .builder()
      .propertyGroups(
        PropertyGroup
          .builder()
          .propertyGroupId(EngineSharedProperties.InternalPropertyGroupId)
          .propertyMap(
            Map(
              "deploymentPropertiesLocationUri" -> deploymentPropertiesLocation.uri,
            ).asJava
          )
          .build()
      )
      .build()

  private def buildEnvironmentPropertiesUpdates(
      deploymentPropertiesLocation: S3ObjectLocation
  ): EnvironmentPropertyUpdates =
    EnvironmentPropertyUpdates
      .builder()
      .propertyGroups(
        PropertyGroup
          .builder()
          .propertyGroupId(EngineSharedProperties.InternalPropertyGroupId)
          .propertyMap(
            Map(
              "deploymentPropertiesLocationUri" -> deploymentPropertiesLocation.uri,
            ).asJava
          )
          .build()
      )
      .build()

  private def buildCreateApplicationRequest(
      applicationName: FlinkApplicationName,
      applicationJarLocation: S3ObjectLocation,
      deploymentPropertiesLocation: S3ObjectLocation
  ) = {
    val codeContent = CodeContent
      .builder()
      .s3ContentLocation(applicationJarLocation.toFlinkClientS3Location)
      .build()

    val appCodeConfig = ApplicationCodeConfiguration
      .builder()
      .codeContent(codeContent)
      .codeContentType(CodeContentType.ZIPFILE)
      .build()

    val flinkConfig = FlinkApplicationConfiguration.builder().build()

    val environmentProperties = buildEnvironmentProperties(deploymentPropertiesLocation)

    val appConfig = ApplicationConfiguration
      .builder()
      .applicationCodeConfiguration(appCodeConfig)
      .environmentProperties(environmentProperties)
      .flinkApplicationConfiguration(flinkConfig)
      .build()

    val loggingConfig = logStreamArn.map(CloudWatchLoggingOption.builder().logStreamARN(_).build()).toList.asJava

    CreateApplicationRequest
      .builder()
      .applicationName(applicationName.value)
      .runtimeEnvironment(runtimeEnviornment)
      .serviceExecutionRole(serviceExecutionRoleArn)
      .cloudWatchLoggingOptions(loggingConfig)
      .applicationConfiguration(appConfig)
      .build()
  }

  private def buildUpdateApplicationRequest(
      applicationDetail: ApplicationDetail,
      applicationJarLocation: S3ObjectLocation,
      deploymentPropertiesLocation: S3ObjectLocation
  ) = {
    val codeContent = CodeContentUpdate
      .builder()
      .s3ContentLocationUpdate(applicationJarLocation.toFlinkClientS3LocationUpdate)
      .build()

    val appCodeConfig = ApplicationCodeConfigurationUpdate
      .builder()
      .codeContentUpdate(codeContent)
      .codeContentTypeUpdate(CodeContentType.ZIPFILE)
      .build()

    val flinkConfig = FlinkApplicationConfigurationUpdate.builder().build()

    val environmentProperties = buildEnvironmentPropertiesUpdates(deploymentPropertiesLocation)

    val appConfig = ApplicationConfigurationUpdate
      .builder()
      .applicationCodeConfigurationUpdate(appCodeConfig)
      .environmentPropertyUpdates(environmentProperties)
      .flinkApplicationConfigurationUpdate(flinkConfig)
      .build()

    val loggingOptionUpdate = logStreamArn.flatMap { logStream =>
      val loggingOptions = applicationDetail.cloudWatchLoggingOptionDescriptions().asScala.toList
      loggingOptions match {
        case firstOption :: Nil => {
          if (firstOption.logStreamARN() == logStream) None
          else
            Some(
              CloudWatchLoggingOptionUpdate
                .builder()
                .cloudWatchLoggingOptionId(firstOption.cloudWatchLoggingOptionId())
                .logStreamARNUpdate(logStream)
                .build()
            )
        }
        // This only handles update for logging option. If its not present, it has to be created in a separate request.
        // TODO: create logging option if not present
        case Nil => None
        case _ :: _ =>
          logger.error(
            s"AWS Managed Flink implementation changed, current Nussknacker version handles updating logging option if there is only one configured. " +
              s"Omitting log stream update for '${applicationDetail.applicationName()}' application."
          )
          None
      }
    }

    val nonFinalUpdateRequest = UpdateApplicationRequest
      .builder()
      .applicationName(applicationDetail.applicationName())
      .currentApplicationVersionId(applicationDetail.applicationVersionId())
      .runtimeEnvironmentUpdate(runtimeEnviornment)
      .serviceExecutionRoleUpdate(serviceExecutionRoleArn)
      .applicationConfigurationUpdate(appConfig)

    (loggingOptionUpdate match {
      case Some(logUpdate) => nonFinalUpdateRequest.cloudWatchLoggingOptionUpdates(logUpdate)
      case None            => nonFinalUpdateRequest
    }).build()
  }

  override def close(): Unit = client.close()
}
