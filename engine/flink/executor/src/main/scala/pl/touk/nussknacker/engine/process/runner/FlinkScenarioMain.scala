package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.{ModelConfigs, ModelData}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.marshall.ScenarioParser

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._
import scala.util.Using
import scala.util.control.NonFatal

object FlinkStandaloneScenarioMain extends FlinkScenarioMain(identity)

// This class is used by external project only
object FlinkK8sScenarioMain extends FlinkScenarioMain(FlinkK8sArgsDecodeHack.prepareProgramArgs)

object CustomArgsFlinkScenarioMain {

  def main(args: Array[String]): Unit = {
    val providers = ServiceLoader.load(classOf[FlinkMainArgsProvider]).iterator().asScala.toList
    val preprocessArgs: Array[String] => Array[String] = providers match {
      case List(one) => one.provideArgs
      case Nil       => throw new IllegalStateException(s"No FlinkScenarioArgsProvider implementations found")
      case multiple =>
        throw new IllegalStateException(s"Multiple FlinkScenarioArgsProvider implementations found: $multiple")
    }
    new FlinkScenarioMain(preprocessArgs).main(args)
  }

}

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
