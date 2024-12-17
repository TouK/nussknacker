package pl.touk.nussknacker.ui.configloader

import cats.effect.IO
import pl.touk.nussknacker.engine.ProcessingTypeConfig

trait ProcessingTypeConfigsLoader {

  def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]]

}
