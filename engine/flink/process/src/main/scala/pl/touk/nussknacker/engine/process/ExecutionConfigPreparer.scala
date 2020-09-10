package pl.touk.nussknacker.engine.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.namespaces.{FlinkUsageKey, NamingContext, ObjectNaming}
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
  def defaultChain(modelData: ModelData, buildInfo: Option[String]): ExecutionConfigPreparer =
    chain(ProcessSettingsPreparer(modelData, buildInfo), SerializationPreparer.fromConfig(modelData.processConfig))

  /**
   * This chain is similar to default one but enableObjectReuse flag from config is omitted and instead of this re-usage is hardcoded to false.
   * This chain is better choice for tests purpose when will be better to check if serialization of messages works correctly.
   */
  def unOptimizedChain(modelData: ModelData, buildInfo: Option[String]): ExecutionConfigPreparer =
    chain(ProcessSettingsPreparer(modelData, buildInfo), new SerializationPreparer(enableObjectReuse = false))

  def chain(configPreparers: ExecutionConfigPreparer*): ExecutionConfigPreparer = {
    new ExecutionConfigPreparer {
      override def prepareExecutionConfig(config: ExecutionConfig)(metaData: MetaData, processVersion: ProcessVersion): Unit = {
        configPreparers.foreach(_.prepareExecutionConfig(config)(metaData, processVersion))
      }
    }
  }

  class ProcessSettingsPreparer(processConfig: Config, objectNaming: ObjectNaming, buildInfo: Option[String]) extends ExecutionConfigPreparer {
    override def prepareExecutionConfig(config: ExecutionConfig)
                                       (metaData: MetaData, processVersion: ProcessVersion): Unit = {
      val namingParameters = objectNaming.objectNamingParameters(metaData.id, processConfig, new NamingContext(FlinkUsageKey))
        .map(p => NamingParameters(p.toTags))
      NkGlobalParameters.setInContext(config, NkGlobalParameters(buildInfo.getOrElse(""), processVersion, processConfig, namingParameters))
    }
  }

  object ProcessSettingsPreparer {
    def apply(modelData: ModelData, buildInfo: Option[String]): ExecutionConfigPreparer = {
      new ProcessSettingsPreparer(modelData.processConfig, modelData.objectNaming, buildInfo)
    }
  }

  class SerializationPreparer(enableObjectReuse: Boolean) extends ExecutionConfigPreparer  {
    override def prepareExecutionConfig(config: ExecutionConfig)
                                       (metaData: MetaData, processVersion: ProcessVersion): Unit = {
      Serializers.registerSerializers(config)
      if (enableObjectReuse) {
        config.enableObjectReuse()
        logger.debug("Object reuse enabled")
      }
    }
  }

  object SerializationPreparer {
    def fromConfig(config: Config): ExecutionConfigPreparer = {
      val enableObjectReuse = config.getOrElse[Boolean]("enableObjectReuse", true)
      new SerializationPreparer(enableObjectReuse)
    }
  }

}
