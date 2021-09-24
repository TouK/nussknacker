package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigRenderOptions}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution.serialize

//intermediate representation of Config, suitable for compact serialization (see ModelConfigLoader). This config should *not* be
//used directly, only after resolving (mainly via ModelData.processConfig)
case class InputConfigDuringExecution(config: Config) {

  lazy val serialized: String = serialize(config)

}

object InputConfigDuringExecution {
  def serialize(config: Config): String = config.root().render(ConfigRenderOptions.concise())
}
