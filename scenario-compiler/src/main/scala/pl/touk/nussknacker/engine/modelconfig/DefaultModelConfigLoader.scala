package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.definition.component.ComponentsFromProvidersExtractor

object DefaultModelConfigLoader extends DefaultModelConfigLoader

trait DefaultModelConfigLoader extends ModelConfigLoader {

  override protected def resolveInputConfigDuringExecution(
      inputConfig: Config,
      configWithDefaults: Config,
      classLoader: ClassLoader
  ): InputConfigDuringExecution = {
    val loaded = ComponentsFromProvidersExtractor(classLoader).loadAdditionalConfig(inputConfig, configWithDefaults)
    InputConfigDuringExecution(loaded)
  }

}
