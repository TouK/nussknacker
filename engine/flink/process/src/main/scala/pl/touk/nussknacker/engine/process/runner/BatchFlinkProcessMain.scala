package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.client.program.OptimizerPlanEnvironment.ProgramAbortException
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.flink.util.FlinkArgsDecodeHack
import pl.touk.nussknacker.engine.process.compiler.BatchStandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.{BatchFlinkProcessRegistrar, FlinkProcessRegistrar}
import pl.touk.nussknacker.engine.util.loader.ProcessConfigCreatorLoader

import scala.util.control.NonFatal

object BatchFlinkProcessMain extends FlinkRunner with LazyLogging {

  def main(argsWithHack: Array[String]) : Unit = {
    try {
      val args = FlinkArgsDecodeHack.prepareProgramArgs(argsWithHack)

      require(args.nonEmpty, "Process json should be passed as a first argument")
      val process = readProcessFromArg(args(0))
      val processVersion = parseProcessVersion(args(1))
      val config: Config = readConfigFromArgs(args)
      val buildInfo = if (args.length > 3) args(3) else ""
      val loadCreator = ProcessConfigCreatorLoader.justOne(Thread.currentThread().getContextClassLoader)
      val registrar = prepareRegistrar(loadCreator, config)
      val env = ExecutionEnvironment.getExecutionEnvironment
      setBuildInfo(buildInfo, processVersion, env)
      registrar.register(env, process, processVersion)
      env.execute(process.id)
    } catch {
      // marker exception for graph optimalization
      case ex: ProgramAbortException =>
        throw ex
      case NonFatal(ex) =>
        logger.error("Unhandled error", ex)
        throw ex
    }
  }

  private def prepareRegistrar(processConfigCreator: ProcessConfigCreator, config: Config): BatchFlinkProcessRegistrar = {
    new BatchStandardFlinkProcessCompiler(processConfigCreator, config).createBatchFlinkProcessRegistrar()
  }

  private def setBuildInfo(buildInfo: String, processVersion: ProcessVersion, env: ExecutionEnvironment): Unit = {
    val globalJobParams = new org.apache.flink.configuration.Configuration
    globalJobParams.setString("buildInfo", buildInfo)
    globalJobParams.setLong("versionId", processVersion.versionId)
    processVersion.modelVersion.foreach(globalJobParams.setLong("modelVersion", _))
    globalJobParams.setString("user", processVersion.user)
    env.getConfig.setGlobalJobParameters(globalJobParams)
  }
}
