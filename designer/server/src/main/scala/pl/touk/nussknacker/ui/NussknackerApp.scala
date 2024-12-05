package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.config.DesignerRootConfigLoader
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory
import pl.touk.nussknacker.ui.loadableconfig.LoadableDesignerRootConfig

object NussknackerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      appFactory <- IO(
        NussknackerAppFactory(LoadableDesignerRootConfig(DesignerRootConfigLoader.load(getClass.getClassLoader)))
      )
      _ <- appFactory.createApp().use { _ => IO.never }
    } yield ExitCode.Success
  }

}
