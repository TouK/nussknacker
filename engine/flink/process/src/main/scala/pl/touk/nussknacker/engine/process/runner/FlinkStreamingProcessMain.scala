package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar

object FlinkStreamingProcessMain extends FlinkProcessMain[StreamExecutionEnvironment] {

  override protected def getExecutionEnvironment: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

  override protected def getConfig(env: StreamExecutionEnvironment): ExecutionConfig = env.getConfig

  override protected def runProcess(env: StreamExecutionEnvironment,
                                    modelData: ModelData,
                                    process: EspProcess,
                                    processVersion: ProcessVersion,
                                    prepareExecutionConfig: ExecutionConfigPreparer): Unit = {
    val compiler = new FlinkProcessCompiler(modelData)
    val registrar: FlinkProcessRegistrar = FlinkProcessRegistrar(compiler, modelData.processConfig, prepareExecutionConfig)
    registrar.register(env, process, processVersion)
    val preparedName = modelData.objectNaming.prepareName(process.id, modelData.processConfig, new NamingContext(FlinkUsageKey))
    env.execute(preparedName)
  }

}
