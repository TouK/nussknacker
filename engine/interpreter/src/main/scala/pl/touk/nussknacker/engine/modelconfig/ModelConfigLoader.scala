package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.modelconfig.ModelConfigLoader.defaultModelConfigResource

object ModelConfigLoader {

  private val defaultModelConfigResource = "defaultModelConfig.conf"

}

/**
  * Loads model configs.
  *
  * Configs are resolved once when loading model data. Implement when you want to add dynamic configuration
  * that is fetched from external source, for example OpenAPI specification and you would like this configuration to
  * be immutable for deployed processes.
  *
  * This class is optional to implement.
  */
abstract class ModelConfigLoader extends Serializable {

  /**
    * Resolves config part to pass during execution while e.g. process deployment on Flink cluster. When running
    * a process (e.g. on Flink) resolved config part is used to construct full config (see
    * [[resolveConfig]])
    *
    * Method used for performance reasons to reduce serialized configuration size inside deployed processes. By default
    * config from main nussknacker file at path: scenarioTypes.{type_name}.modelConfig is passed unchanged.
    *
    * @param inputConfig configuration from scenarioTypes.{type_name}.modelConfig
    * @return config part that is later passed to a running process (see e.g. FlinkProcessCompiler)
    */
  def resolveInputConfigDuringExecution(inputConfig: Config, classLoader: ClassLoader): InputConfigDuringExecution = {
    resolveInputConfigDuringExecution(inputConfig, resolveConfigUsingDefaults(inputConfig, classLoader), classLoader)
  }

  /**
    * Same as [[resolveInputConfigDuringExecution]] but with provided param configWithDefaults containing inputConfig
    * with resolved default values.
    */
  protected def resolveInputConfigDuringExecution(inputConfig: Config, configWithDefaults: Config, classLoader: ClassLoader): InputConfigDuringExecution

  /**
    * Resolves full config used inside [[pl.touk.nussknacker.engine.api.process.ProcessConfigCreator]]. Invoked both
    * in NK and running process.
    *
    * Default implementation [[resolveConfigUsingDefaults]] should rather not be changed.
    */
  def resolveConfig(inputConfigDuringExecution: InputConfigDuringExecution, classLoader: ClassLoader): Config = {
    resolveConfigUsingDefaults(inputConfigDuringExecution.config, classLoader)
  }

  /**
    * Default implementation of [[resolveConfig]]. Loads model config the from following locations:
    * <ol>
    * <li>param inputConfig
    * <li>defaultModelConfig.conf from model jar</li>
    * <li>reference.conf from model jar</li
    * </ol
    */
  protected def resolveConfigUsingDefaults(inputConfig: Config, classLoader: ClassLoader): Config = {
    /*
      We want to be able to embed config in model jar, to avoid excessive config files
      For most cases using reference.conf would work, however there are subtle problems with substitution:
      https://github.com/lightbend/config#note-about-resolving-substitutions-in-referenceconf-and-applicationconf
      https://github.com/lightbend/config/issues/167
      By using separate model.conf we can define configs there like:
      service1Url: ${baseUrl}/service1
      and have baseUrl taken from application config
    */
    val configFallbackFromModel = ConfigFactory.parseResources(classLoader, modelConfigResource)
    //We want to respect overrides (like system properties) and standard fallbacks (like reference.conf)
    ConfigFactory.load(classLoader, inputConfig.withFallback(configFallbackFromModel)).resolve()
  }

  //only for testing
  private[engine] def modelConfigResource: String = defaultModelConfigResource

}
