package pl.touk.nussknacker.engine.process.runner

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.configuration.{Configuration, PipelineOptions}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}

object FlinkStreamingProcessMain extends FlinkProcessMain[StreamExecutionEnvironment] {

  @silent("deprecated") override protected def getExecutionEnvironment: StreamExecutionEnvironment = {
    import scala.collection.JavaConverters._

    val cfg = new Configuration()
    cfg.set(PipelineOptions.CLASSPATHS, List("http://dummy-classpath.invalid").asJava)
    StreamExecutionEnvironment.getExecutionEnvironment(cfg)
  }

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
    val preparedName = modelData.namingStrategy.prepareName(process.name.value)
    env.execute(preparedName)
  }

}
