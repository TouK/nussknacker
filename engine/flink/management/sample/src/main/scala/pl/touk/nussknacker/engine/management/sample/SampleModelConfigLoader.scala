package pl.touk.nussknacker.engine.management.sample

import com.typesafe.config.{Config, ConfigValueFactory}
import pl.touk.nussknacker.engine.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.api.ModelConfigLoader

class SampleModelConfigLoader extends ModelConfigLoader {

  override def resolveFullConfig(configPassedInExecution: Config, classLoader: ClassLoader): Config = {
    new DefaultModelConfigLoader().resolveFullConfig(configPassedInExecution, classLoader)
  }

  override def resolveConfigPassedInExecution(baseModelConfig: Config, classLoader: ClassLoader): Config = {
    baseModelConfig
      .withValue("configLoadedMs", ConfigValueFactory.fromAnyRef(System.currentTimeMillis()))
      .withValue("addedConstantProperty", ConfigValueFactory.fromAnyRef("const"))
  }

}
