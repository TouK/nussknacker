package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext, ObjectNaming}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.flink.api.{NamingParameters, NkGlobalParameters}
import pl.touk.nussknacker.engine.process.util.Serializers

/**
 * This is strategy how Flink's ExecutionConfig will be set up before process registration
 */
trait ExecutionConfigPreparer {

  def prepareExecutionConfig(config: ExecutionConfig)
                            (metaData: MetaData, processVersion: ProcessVersion): Unit

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
      override def prepareExecutionConfig(config: ExecutionConfig)(metaData: MetaData, processVersion: ProcessVersion): Unit = {
        configPreparers.foreach(_.prepareExecutionConfig(config)(metaData, processVersion))
      }
    }
  }

  class ProcessSettingsPreparer(processConfig: Config, objectNaming: ObjectNaming, buildInfo: String) extends ExecutionConfigPreparer {
    override def prepareExecutionConfig(config: ExecutionConfig)
                                       (metaData: MetaData, processVersion: ProcessVersion): Unit = {
      val namingParameters = objectNaming.objectNamingParameters(metaData.id, processConfig, new NamingContext(FlinkUsageKey))
        .map(p => NamingParameters(p.toTags))
      NkGlobalParameters.setInContext(config, NkGlobalParameters(buildInfo, processVersion, processConfig, namingParameters))
    }
  }

  object ProcessSettingsPreparer {
    def apply(modelData: ModelData): ExecutionConfigPreparer = {
      val buildInfo = Encoder[Map[String, String]].apply(modelData.configCreator.buildInfo()).spaces2
      new ProcessSettingsPreparer(modelData.processConfig, modelData.objectNaming, buildInfo)
    }
  }

  class SerializationPreparer(modelData: ModelData) extends ExecutionConfigPreparer  {

    protected def enableObjectReuse: Boolean =
      modelData.processConfig.getOrElse[Boolean]("enableObjectReuse", true)

    override def prepareExecutionConfig(config: ExecutionConfig)
                                       (metaData: MetaData, processVersion: ProcessVersion): Unit = {
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
