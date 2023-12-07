package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.definition.component.ComponentFromProvidersExtractor

class DefaultModelConfigLoader extends ModelConfigLoader {

  override protected def resolveInputConfigDuringExecution(
      inputConfig: Config,
      configWithDefaults: Config,
      classLoader: ClassLoader
  ): InputConfigDuringExecution = {
    val loaded = ComponentFromProvidersExtractor(classLoader).loadAdditionalConfig(inputConfig, configWithDefaults)
    InputConfigDuringExecution(loaded)
  }

}
