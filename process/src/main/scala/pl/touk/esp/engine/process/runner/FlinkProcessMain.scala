package pl.touk.esp.engine.process.runner

import com.typesafe.config.Config
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.process.FlinkProcessRegistrar

object FlinkProcessMain extends FlinkRunner {


  def main(args: Array[String]) : Unit = {

    require(args.nonEmpty, "Process json should be passed as a first argument")
    val process = readProcessFromArg(args(0))
    val config: Config = readConfigFromArgs(args)
    val registrar: FlinkProcessRegistrar = prepareRegistrar(loadCreator(config), config)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    registrar.register(env, process)
    env.execute(process.id)
  }

  private def prepareRegistrar(processConfigCreator: ProcessConfigCreator, config: Config): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(processConfigCreator, config)
  }


}
