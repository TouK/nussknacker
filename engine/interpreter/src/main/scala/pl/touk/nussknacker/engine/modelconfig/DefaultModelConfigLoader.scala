package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config

class DefaultModelConfigLoader extends ModelConfigLoader {

  override def resolveInputConfigDuringExecution(inputConfig: Config, classLoader: ClassLoader): InputConfigDuringExecution = {
    InputConfigDuringExecution(inputConfig)
  }

}
