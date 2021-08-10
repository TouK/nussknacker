package pl.touk.nussknacker.engine.modelconfig

import pl.touk.nussknacker.engine.api.config.LoadedConfig
import pl.touk.nussknacker.engine.component.ComponentExtractor

class DefaultModelConfigLoader extends ModelConfigLoader {

  override protected def resolveInputConfigDuringExecution(inputConfig: LoadedConfig, classLoader: ClassLoader): InputConfigDuringExecution = {
    val unresolvedConfigWithAdditionalConfigs = ComponentExtractor(classLoader).loadAdditionalConfig(inputConfig)
    InputConfigDuringExecution(unresolvedConfigWithAdditionalConfigs)
  }

}
