package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.config.{AlwaysLoadingFileBasedDesignerConfigLoader, DesignerConfigLoader}
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

object NussknackerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      appFactory <- IO(NussknackerAppFactory(new AlwaysLoadingFileBasedDesignerConfigLoader(getClass.getClassLoader)))
      _          <- appFactory.createApp().use { _ => IO.never }
    } yield ExitCode.Success
  }

}
