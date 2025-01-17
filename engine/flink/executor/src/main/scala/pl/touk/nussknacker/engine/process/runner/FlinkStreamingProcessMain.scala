package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.namespaces.NamespaceContext
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}

trait BaseFlinkStreamingProcessMain extends FlinkProcessMain[StreamExecutionEnvironment] {

  override protected def getExecutionEnvironment: StreamExecutionEnvironment =
    StreamExecutionEnvironment.getExecutionEnvironment

  override protected def getConfig(env: StreamExecutionEnvironment): ExecutionConfig = env.getConfig

  override protected def runProcess(
      env: StreamExecutionEnvironment,
      modelData: ModelData,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      prepareExecutionConfig: ExecutionConfigPreparer
  ): Unit = {
    val compilerFactory = new FlinkProcessCompilerDataFactory(modelData)
    val registrar =
      FlinkProcessRegistrar(compilerFactory, FlinkJobConfig.parse(modelData.modelConfig), prepareExecutionConfig)
    registrar.register(env, process, processVersion, deploymentData)
    val preparedName = modelData.namingStrategy.prepareName(process.name.value, NamespaceContext.Flink)
    env.execute(preparedName)
  }

}

object FlinkStreamingProcessMain extends BaseFlinkStreamingProcessMain
