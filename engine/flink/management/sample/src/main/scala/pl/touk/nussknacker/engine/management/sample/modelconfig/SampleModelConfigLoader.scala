package pl.touk.nussknacker.engine.management.sample.modelconfig

import com.typesafe.config.{Config, ConfigValueFactory}
import pl.touk.nussknacker.engine.modelconfig.{InputConfigDuringExecution, ModelConfigLoader}

class SampleModelConfigLoader extends ModelConfigLoader {

  override def resolveInputConfigDuringExecution(inputConfig: Config, classLoader: ClassLoader): InputConfigDuringExecution = {
    val configWithDefaults = resolveConfigUsingDefaults(inputConfig, classLoader)
    InputConfigDuringExecution(
      inputConfig
        .withValue("configLoadedMs", ConfigValueFactory.fromAnyRef(System.currentTimeMillis()))
        .withValue("duplicatedSignalsTopic", ConfigValueFactory.fromAnyRef(configWithDefaults.getString("signalsTopic")))
    )
  }

}
