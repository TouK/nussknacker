package pl.touk.nussknacker.engine.process.runner

import java.io.File
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.{ModelConfigs, ModelData}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer

import scala.util.control.NonFatal

trait FlinkProcessMain[Env] extends FlinkRunner with LazyLogging {

  def main(args: Array[String]): Unit = {
    try {
      require(args.nonEmpty, "Scenario json should be passed as a first argument")
      val process        = readProcessFromArg(args(0))
      val processVersion = parseProcessVersion(args(1))
      val deploymentData = parseDeploymentData(args(2))
      logger.info(
        s"Running deployment ${deploymentData.deploymentId} of scenario ${processVersion.processName} in version ${processVersion.versionId}. " +
          s"Model version ${processVersion.modelVersion}. Deploying user [id=${deploymentData.user.id}, name=${deploymentData.user.name}]"
      )
      val config: Config = readConfigFromArgs(args)
      val modelData      = ModelData.duringFlinkExecution(ModelConfigs(config, deploymentData.additionalModelConfigs))
      val env            = getExecutionEnvironment
      runProcess(
        env,
        modelData,
        process,
        processVersion,
        deploymentData,
        ExecutionConfigPreparer.defaultChain(modelData)
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

  protected def getExecutionEnvironment: Env

  protected def getConfig(env: Env): ExecutionConfig

  protected def runProcess(
      env: Env,
      modelData: ModelData,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      prepareExecutionConfig: ExecutionConfigPreparer
  ): Unit

  private def parseProcessVersion(json: String): ProcessVersion =
    CirceUtil.decodeJsonUnsafe[ProcessVersion](json, "invalid scenario version")

  private def parseDeploymentData(json: String): DeploymentData =
    CirceUtil.decodeJsonUnsafe[DeploymentData](json, "invalid DeploymentData")

  private def readConfigFromArgs(args: Array[String]): Config = {
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
