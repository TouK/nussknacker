package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ModelConfigLoader

/**
  * Used by a running process (e.g. on Flink) to recreate full process config passed to ProcessConfigCreator
  * in execution. This class is serialized in Flink graph. We separate it from the rest of config to keep it small
  * (see e.g. FlinkProcessCompiler). as it's only part that has to be passed to Flink.
  */
case class ModelConfigToLoad(inputConfig: Config, modelConfigLoader: ModelConfigLoader) {

  def loadConfig(classLoader: ClassLoader): Config = {
    modelConfigLoader.resolveFullConfig(inputConfig, classLoader)
  }
}
