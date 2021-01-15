package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ModelConfigLoader

/**
  * Used by a running process on Flink to recreate full process config passed to ProcessConfigCreator
  * using only config passed during process deployment. This class with this config is serialized in Flink graph
  * so we separate it from the rest of configs to keep it small (see e.g. FlinkProcessCompiler), as it's only part
  * that has to be passed to Flink.
  */
case class ModelConfigToLoad(inputConfig: Config, modelConfigLoader: ModelConfigLoader) {

  def loadConfig(classLoader: ClassLoader): Config = {
    modelConfigLoader.resolveFullConfig(inputConfig, classLoader)
  }
}
