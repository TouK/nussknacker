package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.util.loader.ProcessConfigCreatorLoader

object FlinkProcessMain extends FlinkRunner {


  def main(args: Array[String]) : Unit = {

    require(args.nonEmpty, "Process json should be passed as a first argument")
    val process = readProcessFromArg(args(0))
    val config: Config = readConfigFromArgs(args)
    val buildInfo = if (args.length > 2) args(2) else ""
    val loadCreator =      ProcessConfigCreatorLoader.loadProcessConfigCreator(Thread.currentThread().getContextClassLoader)
    val registrar: FlinkProcessRegistrar = prepareRegistrar(loadCreator, config)
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    setBuildInfo(buildInfo, env)
    registrar.register(env, process)
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
