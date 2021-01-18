package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config

/**
  * Used by a running process on Flink to recreate full process config using only config passed during process
  * deployment.
  *
  * This class with config is serialized in Flink graph so we separate it from the rest of configs to keep it
  * small (see e.g. FlinkProcessCompiler), as it's only part that has to be passed to Flink.
  */
case class ModelConfigToLoad(inputConfig: Config, modelConfigLoader: ModelConfigLoader) {

  def loadConfig(classLoader: ClassLoader): Config = {
    modelConfigLoader.resolveConfigDuringExecution(inputConfig, classLoader)
  }
}
