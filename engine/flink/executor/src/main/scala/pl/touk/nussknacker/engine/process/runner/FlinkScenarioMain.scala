package pl.touk.nussknacker.engine.process.runner

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe
import io.circe.Json
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{ModelConfigs, ModelData}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.util.Using
import scala.util.control.NonFatal

object FlinkStandaloneScenarioMain extends FlinkScenarioMain(identity)

// This class is used by external project only
object FlinkK8sScenarioMain extends FlinkScenarioMain(FlinkK8sArgsDecodeHack.prepareProgramArgs)

object AwsManagedFlinkScenarioMain
    extends FlinkScenarioMain({ _ =>
      {
        val appProperties = KinesisAnalyticsRuntime.getApplicationProperties()

        val deploymentPropertiesUri = Option(appProperties.get("nussknacker-internal"))
          .map(_.asScala)
          .flatMap(_.get("deploymentPropertiesLocationUri"))
          .getOrElse(
            throw new IllegalArgumentException(
              "Missing 'deploymentPropertiesLocationUri' in 'nussknacker-internal' group"
            )
          )

        Using(S3Client.builder().build()) { s3Client =>
          val s3Uri  = s3Client.utilities().parseUri(URI.create(deploymentPropertiesUri))
          val bucket = s3Uri.bucket().get()
          val key    = s3Uri.key().get()

          val request                         = GetObjectRequest.builder().bucket(bucket).key(key).build()
          val deploymentPropertiesInputStream = s3Client.getObject(request)

          val deploymentPropertiesString =
            new String(deploymentPropertiesInputStream.readAllBytes(), StandardCharsets.UTF_8)

          circe.parser.decode[Map[String, Json]](deploymentPropertiesString) match {
            case Right(propertiesWithJsonValues) => {
              val properties = propertiesWithJsonValues.view.mapValues(_.spaces2).toMap
              Array(
                properties.getOrElse("scenario", throw new NoSuchElementException("Missing 'scenario' property")),
                properties.getOrElse("version", throw new NoSuchElementException("Missing 'version' property")),
                properties
                  .getOrElse("deploymentData", throw new NoSuchElementException("Missing 'deploymentData' property")),
                properties
                  .getOrElse("modelConfig", throw new NoSuchElementException("Missing 'modelConfig' property"))
              )
            }
            case Left(err) => throw new RuntimeException(s"Failed to decode: $err")
          }
        }.get
      }
    })

class FlinkScenarioMain(preprocessArgs: Array[String] => Array[String]) extends LazyLogging {

  def main(args: Array[String]): Unit = {
    try {
      val preprocessedArgs = preprocessArgs(args)

      require(
        preprocessedArgs.length >= 3,
        "Missing arguments. Usage: CanonicalProcess, ProcessVersion, DeploymentData"
      )
      val scenario       = readScenarioFromArg(preprocessedArgs(0))
      val processVersion = parseProcessVersion(preprocessedArgs(1))
      val deploymentData = parseDeploymentData(preprocessedArgs(2))
      logger.info(
        s"Running deployment ${deploymentData.deploymentId} of scenario ${processVersion.processName} in version ${processVersion.versionId}. " +
          s"Model version ${processVersion.modelVersion}. Deploying user [id=${deploymentData.user.id}, name=${deploymentData.user.name}]"
      )
      val modelConfig = readModelConfigFromArgs(preprocessedArgs)
      val modelData   = ModelData.duringFlinkExecution(ModelConfigs(modelConfig, deploymentData.additionalModelConfigs))
      new FlinkScenarioJob(modelData).run(
        scenario = scenario,
        processVersion = processVersion,
        deploymentData = deploymentData,
        env = StreamExecutionEnvironment.getExecutionEnvironment,
        processListeners = List.empty,
        skipLiveDataUploaderWithReason = None
      )
    } catch {
      // marker exception for graph optimalization
      // should be necessary only in Flink <=1.9
      case ex if ex.getClass.getSimpleName == "ProgramAbortException" =>
        throw ex
      case NonFatal(ex) =>
        logger.error("Unhandled error", ex)
        throw ex
    }
  }

  private def readScenarioFromArg(arg: String): CanonicalProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      Using.resource(scala.io.Source.fromFile(arg.substring(1), StandardCharsets.UTF_8.name()))(_.mkString)
    } else {
      arg
    }
    ScenarioParser.parseUnsafe(canonicalJson)
  }

  private def parseProcessVersion(json: String): ProcessVersion =
    CirceUtil.decodeJsonUnsafe[ProcessVersion](json, "invalid scenario version")

  private def parseDeploymentData(json: String): DeploymentData =
    CirceUtil.decodeJsonUnsafe[DeploymentData](json, "invalid DeploymentData")

  private def readModelConfigFromArgs(args: Array[String]): Config = {
    val optionalConfigArg = if (args.length > 3) Some(args(3)) else None
    readConfigFromArg(optionalConfigArg)
  }

  private def readConfigFromArg(arg: Option[String]): Config =
    arg match {
      case Some(name) if name.startsWith("@") =>
        ConfigFactory.parseFile(new File(name.substring(1)))
      case Some(string) =>
        ConfigFactory.parseString(string)
      case None =>
        ConfigFactory.empty()
    }

}
