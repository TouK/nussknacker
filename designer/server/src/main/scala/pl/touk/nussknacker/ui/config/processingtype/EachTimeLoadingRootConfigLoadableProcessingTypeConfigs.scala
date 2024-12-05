package pl.touk.nussknacker.ui.config.processingtype

import cats.effect.IO
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.ui.config.root.LoadableDesignerRootConfig
import pl.touk.nussknacker.ui.loadableconfig.{DesignerRootConfig, LoadableProcessingTypeConfigs}

class EachTimeLoadingRootConfigLoadableProcessingTypeConfigs(loadableDesignerRootConfig: LoadableDesignerRootConfig)
    extends LoadableProcessingTypeConfigs {

  def loadProcessingTypeConfigs(
      rootConfigLoadedAtStart: DesignerRootConfig
  ): IO[Map[String, ProcessingTypeConfig]] =
    loadableDesignerRootConfig
      .loadDesignerRootConfig()
      .map(LoadableProcessingTypeConfigs.extractProcessingTypeConfigs)

}
