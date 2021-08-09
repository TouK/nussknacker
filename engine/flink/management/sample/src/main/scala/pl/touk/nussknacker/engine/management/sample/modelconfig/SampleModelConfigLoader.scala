package pl.touk.nussknacker.engine.management.sample.modelconfig

import com.typesafe.config.{Config, ConfigValueFactory}
import pl.touk.nussknacker.engine.component.ComponentExtractor
import pl.touk.nussknacker.engine.modelconfig.{InputConfigDuringExecution, ModelConfigLoader}

class SampleModelConfigLoader extends ModelConfigLoader {

  override def resolveInputConfigDuringExecution(inputConfig: Config, resolvedConfigWithDefaults: Config, classLoader: ClassLoader): InputConfigDuringExecution = {
    val withExtractors = ComponentExtractor(classLoader).loadAdditionalConfig(inputConfig, resolvedConfigWithDefaults)
    InputConfigDuringExecution(
      withExtractors
        .withValue("configLoadedMs", ConfigValueFactory.fromAnyRef(System.currentTimeMillis()))
        .withValue("duplicatedSignalsTopic", ConfigValueFactory.fromAnyRef(resolvedConfigWithDefaults.getString("signalsTopic")))
    )
  }

}
