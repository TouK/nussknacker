package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.definition.component.ComponentsFromProvidersExtractor

class DefaultModelConfigLoader(skipComponentProvidersLoadedFromAppClassloader: Boolean) extends ModelConfigLoader {

  override protected def resolveInputConfigDuringExecution(
      inputConfig: Config,
      configWithDefaults: Config,
      classLoader: ClassLoader
  ): InputConfigDuringExecution = {
    val loaded = ComponentsFromProvidersExtractor(classLoader, skipComponentProvidersLoadedFromAppClassloader)
      .loadAdditionalConfig(inputConfig, configWithDefaults)
    InputConfigDuringExecution(loaded)
  }

}
