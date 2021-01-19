package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.modelconfig.ModelConfigLoader.defaultModelConfigResource

object ModelConfigLoader {

  private val defaultModelConfigResource = "defaultModelConfig.conf"

}

/**
  * Loads model configs.
  *
  * Configs are resolved once when loading model data. Implement this trait when you want to add dynamic configuration
  * that is fetched from external source, for example OpenAPI specification and you would like this configuration to
  * be immutable for deployed processes.
  *
  * This trait is optional to implement.
  */
abstract class ModelConfigLoader extends Serializable {

  /**
    * Resolves config part to pass in execution during e.g. process deployment on Flink cluster. When running
    * a process (e.g. on Flink) resolved config part is used to construct full config.
    *
    * Method used for performance reasons to reduce serialized configuration size inside deployed processes. By default
    * config from main nussknacker file at path: processTypes.{type_name}.modelConfig is used.
    *
    * @param configDuringModelAnalysis default config resolved by [[resolveConfigDuringExecution]] for configuration
    *                                  from processTypes.{type_name}.modelConfig
    * @return transformation of config part (processTypes.{type_name}.modelConfig) that is later passed to a running
    *         process (see e.g. FlinkProcessCompiler, ModelConfigToLoad).
    */
  def resolveConfigToPassInExecution(configDuringModelAnalysis: Config, classLoader: ClassLoader): ConfigToPassInExecution

  /**
    * Resolves full config used inside [[pl.touk.nussknacker.engine.api.process.ProcessConfigCreator]]. Invoked both
    * in NK and running process.
    *
    * Default implementation [[resolveConfigUsingDefaults]] should rather not be changed.
    */
  def resolveConfigDuringExecution(configPassedInExecution: Config, classLoader: ClassLoader): Config = {
    resolveConfigUsingDefaults(configPassedInExecution, classLoader)
  }

  /**
    * Default implementation of [[resolveConfigDuringExecution]]. Loads model config the from following locations:
    * <ol>
    * <li> param configPassedInExecution resolved by [[resolveConfigToPassInExecution]], by default part of main config
    * file of nussknacker (in path processTypes.{type_name}.modelConfig)</li>
    * <li>defaultModelConfig.conf from model jar</li>
    * <li>reference.conf from model jar</li
    * </ol
    */
  protected def resolveConfigUsingDefaults(configPassedInExecution: Config, classLoader: ClassLoader): Config = {
    val configFallbackFromModel = ConfigFactory.parseResources(classLoader, modelConfigResource)
    configPassedInExecution
      .withFallback(configFallbackFromModel)
      //this is for reference.conf resources from model jar
      .withFallback(ConfigFactory.load(classLoader))
      .resolve()
  }

  //only for testing
  private[engine] def modelConfigResource: String = defaultModelConfigResource

}
