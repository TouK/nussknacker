package pl.touk.nussknacker.engine.management.sample.modelconfig

import com.typesafe.config.ConfigValueFactory
import pl.touk.nussknacker.engine.api.config.LoadedConfig
import pl.touk.nussknacker.engine.component.ComponentExtractor
import pl.touk.nussknacker.engine.modelconfig.{InputConfigDuringExecution, ModelConfigLoader}

class SampleModelConfigLoader extends ModelConfigLoader {

  override def resolveInputConfigDuringExecution(inputConfig: LoadedConfig, classLoader: ClassLoader): InputConfigDuringExecution = {
    val withExtractors = ComponentExtractor(classLoader).loadAdditionalConfig(inputConfig)
    InputConfigDuringExecution(
      withExtractors
        .withValue("configLoadedMs", ConfigValueFactory.fromAnyRef(System.currentTimeMillis()))
        .withValue("duplicatedSignalsTopic", ConfigValueFactory.fromAnyRef(inputConfig.loadedConfig.getString("signalsTopic")))
    )
  }

}
