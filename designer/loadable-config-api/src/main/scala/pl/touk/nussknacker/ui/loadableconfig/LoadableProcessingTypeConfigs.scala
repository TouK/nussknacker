package pl.touk.nussknacker.ui.loadableconfig

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import cats.effect.IO

trait LoadableProcessingTypeConfigs {

  // rootConfigLoadedAtStart is used for external project purpose - don't remove it
  def loadProcessingTypeConfigs(rootConfigLoadedAtStart: DesignerRootConfig): IO[Map[String, ProcessingTypeConfig]]

}

object LoadableProcessingTypeConfigs extends LazyLogging {

  def extractProcessingTypeConfigs(rootConfig: DesignerRootConfig): Map[String, ProcessingTypeConfig] = {
    rootConfig.rawConfig
      .readMap("scenarioTypes")
      .getOrElse {
        throw new RuntimeException("No scenario types configuration provided")
      }
      .mapValuesNow(ProcessingTypeConfig.read)
  }

}
