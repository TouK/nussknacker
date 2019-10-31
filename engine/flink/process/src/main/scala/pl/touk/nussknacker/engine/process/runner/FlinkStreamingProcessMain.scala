package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.compiler.FlinkStreamingProcessCompiler

object FlinkStreamingProcessMain extends FlinkProcessMain[StreamExecutionEnvironment] {

  override protected def getExecutionEnvironment: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

  override protected def getConfig(env: StreamExecutionEnvironment): ExecutionConfig = env.getConfig

  override protected def runProcess(env: StreamExecutionEnvironment,
                                    configCreator: ProcessConfigCreator,
                                    config: Config,
                                    process: EspProcess,
                                    processVersion: ProcessVersion): Unit = {
    val compiler = new FlinkStreamingProcessCompiler(configCreator, config)
    val registrar = compiler.createFlinkProcessRegistrar()
    registrar.register(env, process, processVersion)
    env.execute(process.id)
  }
}
