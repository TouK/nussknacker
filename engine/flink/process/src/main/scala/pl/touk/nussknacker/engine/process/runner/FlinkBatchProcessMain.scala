package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala.ExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkBatchProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler

object FlinkBatchProcessMain extends FlinkProcessMain[ExecutionEnvironment] {

  override protected def getExecutionEnvironment: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment

  override protected def getConfig(env: ExecutionEnvironment): ExecutionConfig = env.getConfig

  override protected def runProcess(env: ExecutionEnvironment,
                                    modelData: ModelData,
                                    process: EspProcess,
                                    processVersion: ProcessVersion): Unit = {
    val compiler = new FlinkProcessCompiler(modelData)
    val registrar = FlinkBatchProcessRegistrar(compiler, modelData.processConfig)
    registrar.register(env, process, processVersion)
    env.execute(process.id)
  }
}
