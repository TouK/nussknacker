package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config

class DefaultModelConfigLoader extends ModelConfigLoader {

  override def resolveConfigToPassInExecution(configDuringModelAnalysis: Config, classLoader: ClassLoader): ConfigToPassInExecution = {
    new ConfigToPassInExecution {
      override def transform(config: Config): Config = config
    }
  }

}
