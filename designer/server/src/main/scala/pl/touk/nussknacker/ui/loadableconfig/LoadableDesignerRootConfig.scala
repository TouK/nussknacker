package pl.touk.nussknacker.ui.loadableconfig

import cats.effect.IO
import pl.touk.nussknacker.ui.config.DesignerRootConfig

trait LoadableDesignerRootConfig {

  def loadDesignerRootConfig(): IO[DesignerRootConfig]

}

object LoadableDesignerRootConfig {

  def apply(loadConfig: IO[DesignerRootConfig]): LoadableDesignerRootConfig = { () =>
    loadConfig
  }

}
