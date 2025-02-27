package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.{NamespaceMetricsTags, NkGlobalParameters}
import pl.touk.nussknacker.engine.flink.api.typeinformation.FlinkTypeInfoRegistrar
import pl.touk.nussknacker.engine.process.util.Serializers

/**
 * This is strategy how Flink's ExecutionConfig will be set up before process registration
 */
trait ExecutionConfigPreparer {

  def prepareExecutionConfig(config: ExecutionConfig)(jobData: JobData, deploymentData: DeploymentData): Unit

}

object ExecutionConfigPreparer extends LazyLogging {

  /**
   * This is the default chain po ExecutionConfigPreparers that should be used in production code
   */
  def defaultChain(modelData: ModelData): ExecutionConfigPreparer =
    chain(ProcessSettingsPreparer(modelData), new SerializationPreparer(modelData))

  /**
   * This chain is similar to default one but enableObjectReuse flag from config is omitted and instead of this re-usage is hardcoded to false.
   * This chain is better choice for tests purpose when will be better to check if serialization of messages works correctly.
   */
  def unOptimizedChain(modelData: ModelData): ExecutionConfigPreparer =
    chain(ProcessSettingsPreparer(modelData), new UnoptimizedSerializationPreparer(modelData))

  def chain(configPreparers: ExecutionConfigPreparer*): ExecutionConfigPreparer = {
    new ExecutionConfigPreparer {
      override def prepareExecutionConfig(
          config: ExecutionConfig
      )(jobData: JobData, deploymentData: DeploymentData): Unit = {
        configPreparers.foreach(_.prepareExecutionConfig(config)(jobData, deploymentData))
      }
    }
  }

  class ProcessSettingsPreparer(modelConfig: Config, namingStrategy: NamingStrategy, modelInfo: ModelInfo)
      extends ExecutionConfigPreparer {

    override def prepareExecutionConfig(
        config: ExecutionConfig
    )(jobData: JobData, deploymentData: DeploymentData): Unit = {
      config.setGlobalJobParameters(
        NkGlobalParameters.create(
          modelInfo,
          deploymentData.deploymentId.value,
          jobData.processVersion,
          modelConfig,
          namespaceTags = NamespaceMetricsTags(jobData.metaData.name.value, namingStrategy),
          prepareMap(deploymentData)
        )
      )
    }

    private def prepareMap(deploymentData: DeploymentData) =
      deploymentData.additionalDeploymentData.map { case (k, v) =>
        s"deployment.properties.$k" -> v
      }

  }

  object ProcessSettingsPreparer {

    def apply(modelData: ModelData): ExecutionConfigPreparer = {
      new ProcessSettingsPreparer(modelData.modelConfig, modelData.namingStrategy, modelData.info)
    }

  }

  class SerializationPreparer(modelData: ModelData) extends ExecutionConfigPreparer {

    protected def enableObjectReuse: Boolean =
      modelData.modelConfig.getOrElse[Boolean]("enableObjectReuse", true)

    override def prepareExecutionConfig(
        config: ExecutionConfig
    )(jobData: JobData, deploymentData: DeploymentData): Unit = {
      FlinkTypeInfoRegistrar.ensureTypeInfosAreRegistered()
      Serializers.registerSerializers(modelData, config)
      if (enableObjectReuse) {
        config.enableObjectReuse()
        logger.debug("Object reuse enabled")
      }
    }

  }

  class UnoptimizedSerializationPreparer(modelData: ModelData) extends SerializationPreparer(modelData) {
    override protected def enableObjectReuse: Boolean = false
  }

}
