package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config

class DefaultModelConfigLoader extends ModelConfigLoader {

  override protected def resolveInputConfigDuringExecution(inputConfig: Config, configWithDefaults: Config, classLoader: ClassLoader): InputConfigDuringExecution = {
    InputConfigDuringExecution(inputConfig)
  }

}
