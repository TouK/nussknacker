package pl.touk.nussknacker.engine.standalone

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ModelData

object StandaloneModelData {

  def apply(config: Config = ConfigFactory.load()): ModelData = {
    ModelData(config, "standaloneConfig", "standaloneProcessConfig")
  }

}
