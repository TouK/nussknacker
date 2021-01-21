package pl.touk.nussknacker.engine.process.runner

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.flink.util.FlinkArgsDecodeHack
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer

import scala.util.control.NonFatal

trait FlinkProcessMain[Env] extends FlinkRunner with LazyLogging {

  def main(argsWithHack: Array[String]): Unit = {
    try {
      val args = FlinkArgsDecodeHack.prepareProgramArgs(argsWithHack)

      require(args.nonEmpty, "Process json should be passed as a first argument")
      val process = readProcessFromArg(args(0))
      val processVersion = parseProcessVersion(args(1))
      val config: Config = readConfigFromArgs(args)
      val modelData = ModelData.duringExecution(config)
      val env = getExecutionEnvironment
      runProcess(env, modelData, process, processVersion, ExecutionConfigPreparer.defaultChain(modelData))
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

  protected def runProcess(env: Env,
                           modelData: ModelData,
                           process: EspProcess,
                           processVersion: ProcessVersion,
                           prepareExecutionConfig: ExecutionConfigPreparer): Unit

  private def parseProcessVersion(json: String): ProcessVersion =
    CirceUtil.decodeJsonUnsafe[ProcessVersion](json, "invalid process version")

  private def readConfigFromArgs(args: Array[String]): Config = {
    val optionalConfigArg = if (args.length > 2) Some(args(2)) else None
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
