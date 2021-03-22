package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.{Config, ConfigRenderOptions}

case class InputConfigDuringExecution(config: Config) {

  lazy val serialized: String = config.root().render(ConfigRenderOptions.concise())

}
