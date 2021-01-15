package pl.touk.nussknacker.engine.api

import com.typesafe.config.Config

/**
  * Loads model configs. Base config comes from processTypes.{typeName}.modelConfig.
  *
  * Configs are resolved once when loading model data. Implement this trait when you want to add dynamic configuration
  * that is fetched from external source, for example OpenAPI specification and you would like this configuration to
  * be immutable for deployed processes.
  *
  * This trait is optional to implement. Default implementations complements base config with configuration
  * available in JAR's resources.
  */
trait ModelConfigLoader extends Serializable {

  /**
    *  Resolves full config used inside [[pl.touk.nussknacker.engine.api.process.ProcessConfigCreator]]. Invoked both
    *  in NK and running process.
    */
  def resolveFullConfig(configPassedInExecution: Config, classLoader: ClassLoader): Config

  /**
    * Resolves partial config that is passed in execution during e.g. process deployment on Flink cluster. When running
    * a process on engine (e.g. on Flink) this config is used to construct full config.
    *
    * It is used for performance reasons to reduce serialized configuration size inside deployed processes.
    */
  def resolveConfigPassedInExecution(baseModelConfig: Config, classLoader: ClassLoader): Config

}
