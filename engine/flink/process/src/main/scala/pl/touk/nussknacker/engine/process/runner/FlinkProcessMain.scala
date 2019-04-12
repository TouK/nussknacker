package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.flink.util.FlinkArgsDecodeHack
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.util.loader.ProcessConfigCreatorLoader

object FlinkProcessMain extends FlinkRunner with LazyLogging {


  def main(argsWithHack: Array[String]) : Unit = {
    val args =  FlinkArgsDecodeHack.prepareProgramArgs(argsWithHack)

    require(args.nonEmpty, "Process json should be passed as a first argument")
    //TODO: Too many arguments.
    val process = readProcessFromArg(args(0))
    val processVersion = parseProcessVersion(args(1))
    val config: Config = readConfigFromArgs(args)
    val buildInfo = if (args.length > 3) args(3) else ""
    val loadCreator = ProcessConfigCreatorLoader.justOne(Thread.currentThread().getContextClassLoader)
    val registrar: FlinkProcessRegistrar = prepareRegistrar(loadCreator, config)
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    setBuildInfo(buildInfo, env)
    registrar.register(env, process, processVersion)
    env.execute(process.id)
  }

  private def prepareRegistrar(processConfigCreator: ProcessConfigCreator, config: Config): FlinkProcessRegistrar = {
    new StandardFlinkProcessCompiler(processConfigCreator, config).createFlinkProcessRegistrar()
  }

  private def setBuildInfo(buildInfo: String, env: StreamExecutionEnvironment) = {
    val globalJobParams = new org.apache.flink.configuration.Configuration
    globalJobParams.setString("buildInfo", buildInfo)
    env.getConfig.setGlobalJobParameters(globalJobParams)
  }


}
