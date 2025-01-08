package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.config.AlwaysLoadingFileBasedDesignerConfigLoader
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

object NussknackerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    program.useForever.as(ExitCode.Success)
  }

  private def program = for {
    appFactory <- NussknackerAppFactory.create(AlwaysLoadingFileBasedDesignerConfigLoader(getClass.getClassLoader))
    _          <- appFactory.createApp()
  } yield ()

}
