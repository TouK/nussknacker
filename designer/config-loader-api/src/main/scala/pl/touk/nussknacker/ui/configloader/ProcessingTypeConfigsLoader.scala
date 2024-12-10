package pl.touk.nussknacker.ui.configloader

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import cats.effect.IO

trait ProcessingTypeConfigsLoader {

  def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]]

}

object ProcessingTypeConfigsLoader extends LazyLogging {

  def extractProcessingTypeConfigs(rootConfig: DesignerRootConfig): Map[String, ProcessingTypeConfig] = {
    rootConfig.rawConfig
      .readMap("scenarioTypes")
      .getOrElse {
        throw new RuntimeException("No scenario types configuration provided")
      }
      .mapValuesNow(ProcessingTypeConfig.read)
  }

}
