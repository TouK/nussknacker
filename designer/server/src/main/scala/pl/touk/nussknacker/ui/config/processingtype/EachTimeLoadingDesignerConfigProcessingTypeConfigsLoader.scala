package pl.touk.nussknacker.ui.config.processingtype

import cats.effect.IO
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigsLoader

class EachTimeLoadingDesignerConfigProcessingTypeConfigsLoader(designerConfigLoader: DesignerConfigLoader)
    extends ProcessingTypeConfigsLoader {

  def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]] =
    designerConfigLoader
      .loadDesignerConfig()
      .map(_.processingTypeConfigs)

}
