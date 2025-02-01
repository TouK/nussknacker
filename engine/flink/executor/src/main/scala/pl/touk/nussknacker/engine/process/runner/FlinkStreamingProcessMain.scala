package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.marshall.ScenarioParser

import java.io.File
import java.nio.charset.StandardCharsets
import scala.util.Using
import scala.util.control.NonFatal

class BaseFlinkStreamingProcessMain extends LazyLogging {

  def main(argsWithHack: Array[String]): Unit = {
    try {
      val args = FlinkArgsDecodeHack.prepareProgramArgs(argsWithHack)

      require(args.nonEmpty, "Scenario json should be passed as a first argument")
      val process        = readScenarioFromArg(args(0))
      val processVersion = parseProcessVersion(args(1))
      val deploymentData = parseDeploymentData(args(2))
      logger.info(
        s"Running deployment ${deploymentData.deploymentId} of scenario ${processVersion.processName} in version ${processVersion.versionId}. " +
          s"Model version ${processVersion.modelVersion}. Deploying user [id=${deploymentData.user.id}, name=${deploymentData.user.name}]"
      )
      val modelConfig = readModelConfigFromArgs(args)
      FlinkScenarioJob.runScenario(
        process,
        processVersion,
        deploymentData,
        modelConfig,
        StreamExecutionEnvironment.getExecutionEnvironment
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

object FlinkStreamingProcessMain extends BaseFlinkStreamingProcessMain
