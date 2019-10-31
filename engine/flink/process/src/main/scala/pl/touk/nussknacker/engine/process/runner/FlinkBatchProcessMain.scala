package pl.touk.nussknacker.engine.process.runner

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala.ExecutionEnvironment
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.compiler.FlinkBatchProcessCompiler

object FlinkBatchProcessMain extends FlinkProcessMain[ExecutionEnvironment] {

  override protected def getExecutionEnvironment: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment

  override protected def getConfig(env: ExecutionEnvironment): ExecutionConfig = env.getConfig

  override protected def runProcess(env: ExecutionEnvironment,
                                    configCreator: ProcessConfigCreator,
                                    config: Config,
                                    process: EspProcess,
                                    processVersion: ProcessVersion): Unit = {
    val compiler = new FlinkBatchProcessCompiler(configCreator, config)
    val registrar = compiler.createFlinkProcessRegistrar()
    registrar.register(env, process, processVersion)
    env.execute(process.id)
  }
}
