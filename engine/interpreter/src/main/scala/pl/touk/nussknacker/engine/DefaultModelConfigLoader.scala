package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.DefaultModelConfigLoader.defaultModelConfigResource
import pl.touk.nussknacker.engine.api.ModelConfigLoader

object DefaultModelConfigLoader {

  private val defaultModelConfigResource = "defaultModelConfig.conf"

}

class DefaultModelConfigLoader extends ModelConfigLoader {

  /**
    * Config of model can come from following locations:
    * - configPassedInExecution, part of main config file of nussknacker (in path processTypes.{type_name}.modelConfig),
    *  returned in [[resolveConfigPassedInExecution]]
    * - defaultModelConfig.conf from model jar
    * - reference.conf from model jar
    */
  override def resolveFullConfig(configPassedInExecution: Config, classLoader: ClassLoader): Config = {
    val configFallbackFromModel = ConfigFactory.parseResources(classLoader, modelConfigResource)
    configPassedInExecution
      .withFallback(configFallbackFromModel)
      //this is for reference.conf resources from model jar
      .withFallback(ConfigFactory.load(classLoader))
      .resolve()
  }

  override def resolveConfigPassedInExecution(baseModelConfig: Config, classLoader: ClassLoader): Config = {
    baseModelConfig
  }

  //only for testing
  private[engine] def modelConfigResource: String = defaultModelConfigResource
}
