package pl.touk.nussknacker.engine.management

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.ModelData

object FlinkModelData {

  def apply(config: Config = ConfigFactory.load()): ModelData = {
    ModelData(config, "flinkConfig.jarPath", "processConfig")
  }

}
