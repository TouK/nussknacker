package pl.touk.nussknacker.engine.aws.managedflink

import cats.effect.unsafe.IORuntime
import cats.effect.unsafe.implicits.global
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Network
import org.scalatest.time.{Minutes, Second, Seconds, Span}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{DesignerWideComponentId, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.classloader.{DeploymentManagersClassLoaderFactory, ModelClassLoaderFactory}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData, DeploymentId, User}
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kinesisanalyticsv2.KinesisAnalyticsV2Client
import software.amazon.awssdk.services.kinesisanalyticsv2.model._
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

//  Test requires following AWS setup:
//  1. Dedicated S3 bucket in which application jar and config will be stored.
//     The test does not clean up these uploaded files. Cleanup can be done by configuring lifecycle on the bucket so that objects expire after some time after upload.
//  2. Dedicated CloudWatch Log stream.
//     The test does not clean up the logs it writes. Cleanup can be done by setting retention on Log group.
//  3. Service role with at least permissions like in policy and trust policy for KinesisAnalytics service allowing to assume this role:
//  {
//    "Version": "2012-10-17",
//    "Statement": [
//      {
//        "Sid": "S3ReadJarAndConfig",
//        "Effect": "Allow",
//        "Action": [
//          "s3:GetObject",
//          "s3:GetObjectVersion"
//        ],
//        "Resource": "arn:aws:s3:::<BUCKET_NAME>/*"
//      },
//      {
//        "Sid": "CloudWatchLogs",
//        "Effect": "Allow",
//        "Action": [
//          "logs:DescribeLogGroups",
//          "logs:DescribeLogStreams",
//          "logs:CreateLogGroup",
//          "logs:CreateLogStream",
//          "logs:PutLogEvents"
//        ],
//        "Resource": "<LOG_STREAM_ARN>"
//      }
//    ]
//  }
//  {
//    "Version": "2012-10-17",
//    "Statement": [{
//      "Effect": "Allow",
//      "Principal": {
//        "Service": "kinesisanalytics.amazonaws.com"
//      },
//      "Action": "sts:AssumeRole"
//    }]
//  }
//  4. IAM User with at least permissions like in policy:
//  {
//  	"Version": "2012-10-17",
//  	"Statement": [
//  		{
//  			"Sid": "S3Upload",
//  			"Effect": "Allow",
//  			"Action": [
//  				"s3:PutObject"
//  			],
//  			"Resource": "<AWS_S3_BUCKET>"
//  		},
//  		{
//  			"Sid": "KinesisAnalytics",
//  			"Effect": "Allow",
//  			"Action": [
//  				"kinesisanalytics:DescribeApplication",
//  				"kinesisanalytics:CreateApplication",
//  				"kinesisanalytics:UpdateApplication",
//  				"kinesisanalytics:StartApplication",
//  				"kinesisanalytics:StopApplication"
//  			],
//  			"Resource": "*"
//  		},
//  		{
//  			"Sid": "PassServiceRole",
//  			"Effect": "Allow",
//  			"Action": "iam:PassRole",
//  			"Resource": "<AWS_CLOUDWATCH_LOG_STREAM_ARN>"
//  		}
//  	]
//  }
@Network
class AwsManagedFlinkDeploymentManagerIntegrationTest
    extends AnyFunSuite
    with Matchers
    with Eventually
    with ScalaFutures
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll
    with LazyLogging {

  implicit val pc: PatienceConfig = PatienceConfig(Span(40, Seconds), Span(1, Second))

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import FlinkApplicationName.ScenarioNameOps

  private val awsAccessKeyId = Option(System.getenv("AWS_ACCESS_KEY_ID"))
    .getOrElse(throw new RuntimeException("AWS_ACCESS_KEY_ID environment variable is required"))
  private val awsSecretAccessKey = Option(System.getenv("AWS_SECRET_ACCESS_KEY"))
    .getOrElse(throw new RuntimeException("AWS_SECRET_ACCESS_KEY environment variable is required"))
  private val awsRegion = Option(System.getenv("AWS_REGION"))
    .getOrElse(throw new RuntimeException("AWS_REGION environment variable is required"))
  private val awsS3Bucket = Option(System.getenv("AWS_S3_BUCKET"))
    .getOrElse(throw new RuntimeException("AWS_S3_BUCKET environment variable is required"))

  private val awsManagedFlinkServiceExecutionRoleArn =
    Option(System.getenv("AWS_MANAGED_FLINK_SERVICE_EXECUTION_ROLE_ARN")).getOrElse(
      throw new RuntimeException("AWS_MANAGED_FLINK_SERVICE_EXECUTION_ROLE_ARN environment variable is required")
    )

  private val awsManagedFlinkLogStreamArn =
    Option(System.getenv("AWS_CLOUDWATCH_LOG_STREAM_ARN"))
      .getOrElse(throw new RuntimeException("AWS_CLOUDWATCH_LOG_STREAM_ARN environment variable is required"))

  private val (deploymentManagersClassLoader, releaseDeploymentManagerClassLoader) =
    DeploymentManagersClassLoaderFactory.create(List.empty).allocated.unsafeRunSync()(IORuntime.global)

  private val deploymentManager = {
    val processingTypeConfig = ConfigWithUnresolvedVersion(
      ConfigFactory.parseString(
        s"""
        |deploymentConfig {
        |  type: "awsManagedFlink"
        |  awsAccessKeyId: "$awsAccessKeyId"
        |  awsSecretAccessKey: "$awsSecretAccessKey"
        |  region: "$awsRegion"
        |  bucketName: "$awsS3Bucket"
        |  serviceExecutionRoleArn: "$awsManagedFlinkServiceExecutionRoleArn"
        |  cloudWatchLogStreamArn: "$awsManagedFlinkLogStreamArn"
        |}
        |modelConfig {
        |  classPath: [
        |    "./engine/flink/executor/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkExecutor.jar",
        |    "./defaultModel/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/defaultModel.jar"
        |    "./engine/flink/components/base/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBase.jar",
        |    "./engine/flink/components/base-unbounded/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/flinkBaseUnbounded.jar",
        |    "./engine/flink/aws-managed-flink-dependencies/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/awsManagedFlinkDependencies.jar"
        |  ]
        |}
        |category: "Default"
        |""".stripMargin
      )
    )
    val typeConfig       = ProcessingTypeConfig.read(processingTypeConfig)
    val modelClassLoader = ModelClassLoaderFactory.create(typeConfig.classPath, None, deploymentManagersClassLoader)
    val modelData = ModelData(
      processingTypeConfig = typeConfig,
      ModelDependencies(
        additionalConfigsFromProvider = Map.empty,
        determineDesignerWideId = id => DesignerWideComponentId(id.toString),
        workingDirectoryOpt = None,
        componentDefinitionExtractionMode = ComponentDefinitionExtractionMode.FinalDefinition,
        designerDbRef = None
      ),
      modelClassLoader
    )
    val deps = new DeploymentManagerDependencies(
      ExecutionContext.global,
      IORuntime.global,
      ActorSystem(getClass.getSimpleName),
      SttpBackendStub.asynchronousFuture
    )
    new AwsManagedFlinkDeploymentManagerProvider()
      .createDeploymentManager(
        modelDataProvider = modelData.toModelDataProvider,
        dependencies = deps,
        deploymentConfig = typeConfig.deploymentConfig,
        scenarioStateCacheTTL = None
      )
      .validValue
  }

  private val kinesisAnalyticsClient = KinesisAnalyticsV2Client
    .builder()
    .region(Region.of(awsRegion))
    .credentialsProvider(
      StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey))
    )
    .build()

  private val scenarioName    = s"test_${System.currentTimeMillis()}"
  private val applicationName = ProcessName(scenarioName).toFlinkApplicationName

  test("deploy and cancel application") {
    val user = User("testuser", "Test User")
    val defaultVersion =
      ProcessVersion(VersionId.initialVersionId, ProcessName(scenarioName), ProcessId(1), List.empty, user.id, None)

    val defaultDeploymentData = DeploymentData(
      DeploymentId(""),
      user,
      Map.empty,
      NodesDeploymentData.empty,
      AdditionalModelConfigs.empty
    )

    val scenario = ScenarioBuilder
      .streaming(scenarioName)
      .source(
        "start",
        "event-generator",
        "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
        "count"    -> "1".spel,
        "value"    -> s"'payload'".spel
      )
      .emptySink("end", "dead-end")

    val runCommand = DMRunDeploymentCommand(
      defaultVersion,
      defaultDeploymentData,
      scenario,
      DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
        StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
      )
    )

    deploymentManager.processCommand(runCommand).futureValue

    eventually(Timeout(Span(3, Minutes)), Interval(Span(5, Seconds))) {
      fetchApplicationDetail(scenarioName).applicationStatus() shouldBe ApplicationStatus.RUNNING
    }

    val cancelCommand = DMCancelScenarioCommand(ProcessName(scenarioName), user)

    deploymentManager.processCommand(cancelCommand).futureValue

    eventually(Timeout(Span(3, Minutes)), Interval(Span(5, Seconds))) {
      fetchApplicationDetail(scenarioName).applicationStatus() shouldBe ApplicationStatus.READY
    }
  }

  private def fetchApplicationDetail(applicationName: String): ApplicationDetail = {
    val response = kinesisAnalyticsClient.describeApplication(
      DescribeApplicationRequest.builder().applicationName(applicationName).build()
    )
    response.applicationDetail()
  }

  private def deleteApplicationIfExists(applicationName: FlinkApplicationName): Unit = {
    try {
      logger.info(s"Attempting deletion of AWS Managed Flink application '${applicationName.value}'")
      val detail = fetchApplicationDetail(applicationName.value)
      kinesisAnalyticsClient.deleteApplication(
        DeleteApplicationRequest
          .builder()
          .applicationName(applicationName.value)
          .createTimestamp(detail.createTimestamp())
          .build()
      )
      logger.info(s"AWS Managed Flink application '${applicationName.value}' successfully deleted")
    } catch {
      case _: ResourceNotFoundException => logger.info("Application on AWS Managed Flink not found. Omitting cleanup.")
    }
  }

  override protected def afterAll(): Unit = {
    try {
      deleteApplicationIfExists(applicationName)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"Failed to clean up AWS Managed Flink application: ${applicationName.value}", ex)
    } finally {
      releaseDeploymentManagerClassLoader.unsafeRunSync()
      deploymentManager.close()
      kinesisAnalyticsClient.close()
      super.afterAll()
    }
  }

}
