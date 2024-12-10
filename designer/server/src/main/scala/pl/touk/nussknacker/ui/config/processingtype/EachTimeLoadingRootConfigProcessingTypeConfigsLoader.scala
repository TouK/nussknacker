package pl.touk.nussknacker.ui.config.processingtype

import cats.effect.IO
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.ui.config.root.DesignerRootConfigLoader
import pl.touk.nussknacker.ui.configloader.ProcessingTypeConfigsLoader

class EachTimeLoadingRootConfigProcessingTypeConfigsLoader(designerRootConfigLoader: DesignerRootConfigLoader)
    extends ProcessingTypeConfigsLoader {

  def loadProcessingTypeConfigs(): IO[Map[String, ProcessingTypeConfig]] =
    designerRootConfigLoader
      .loadDesignerRootConfig()
      .map(ProcessingTypeConfigsLoader.extractProcessingTypeConfigs)

}
