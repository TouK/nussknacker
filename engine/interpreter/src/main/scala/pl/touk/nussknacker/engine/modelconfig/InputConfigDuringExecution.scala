package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigRenderOptions}

//intermediate representation of Config, suitable for compact serialization (see ModelConfigLoader). This config should *not* be
//used directly, only after resolving (mainly via ModelData.processConfig)
case class InputConfigDuringExecution(config: Config) {

  lazy val serialized: String = config.root().render(ConfigRenderOptions.concise())

}
