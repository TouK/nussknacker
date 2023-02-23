package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
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
abstract class ModelConfigLoader extends Serializable with LazyLogging {

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
  final def resolveInputConfigDuringExecution(inputConfig: ConfigWithUnresolvedVersion, classLoader: ClassLoader): InputConfigDuringExecution = {
    val (potentiallyLoadedInputConfig, potentiallyLoadedConfigWithDefaults) = if (shouldLoadInputConfigDuringExecution) {
      logger.debug("Loading input model config")
      val configWithDefaults = withFallbackToDefaults(inputConfig.resolved, classLoader)
      (inputConfig.resolved, ConfigFactory.load(classLoader, configWithDefaults))
    } else {
      logger.debug("Skipping input model config load")
      val configWithDefaults = withFallbackToDefaults(inputConfig.withUnresolvedEnvVariables, classLoader)
      (inputConfig.withUnresolvedEnvVariables, configWithDefaults)
    }
    resolveInputConfigDuringExecution(potentiallyLoadedInputConfig, potentiallyLoadedConfigWithDefaults, classLoader)
  }

  /**
    * We want to be able to turn off loading model config before passing it to execution.
    * It is for purpose where engine (e.g. Flink) see other host names / ports than designer.
    * It is especially useful for local development. Remember that when you turn it off, you can't
    * use optional substitutions (e.g. ${?KAFKA_ADDRESS} in model config. Otherwise they will be removed.
    */
  protected def shouldLoadInputConfigDuringExecution: Boolean = sys.env.get("LOAD_INPUT_CONFIG").forall(_.toBoolean)

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
  final protected def resolveConfigUsingDefaults(inputConfig: Config, classLoader: ClassLoader): Config = {
    /*
      We want to be able to embed config in model jar, to avoid excessive config files
      For most cases using reference.conf would work, however there are subtle problems with substitution:
      https://github.com/lightbend/config#note-about-resolving-substitutions-in-referenceconf-and-applicationconf
      https://github.com/lightbend/config/issues/167
      By using separate model.conf we can define configs there like:
      service1Url: ${baseUrl}/service1
      and have baseUrl taken from application config
    */
    //We want to respect overrides (like system properties) and standard fallbacks (like reference.conf)
    ConfigFactory.load(classLoader, withFallbackToDefaults(inputConfig, classLoader))
  }

  final protected def withFallbackToDefaults(inputConfig: Config, classLoader: ClassLoader): Config = {
    val configFallbackFromModel = ConfigFactory.parseResources(classLoader, modelConfigResource)
    inputConfig.withFallback(configFallbackFromModel)
  }

  //only for testing
  private[engine] def modelConfigResource: String = defaultModelConfigResource

}
