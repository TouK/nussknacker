package pl.touk.nussknacker.engine.management.sample.modelconfig

import com.typesafe.config.{Config, ConfigValueFactory}
import pl.touk.nussknacker.engine.modelconfig.{ConfigToPassInExecution, ModelConfigLoader}

class SampleModelConfigLoader extends ModelConfigLoader {

  override def resolveConfigToPassInExecution(configDuringModelAnalysis: Config, classLoader: ClassLoader): ConfigToPassInExecution = {
    new ConfigToPassInExecution {
      override def transform(config: Config): Config = {
        config
          .withValue("configLoadedMs", ConfigValueFactory.fromAnyRef(System.currentTimeMillis()))
          .withValue("duplicatedSignalsTopic", ConfigValueFactory.fromAnyRef(configDuringModelAnalysis.getString("signalsTopic")))
      }
    }
  }

}
