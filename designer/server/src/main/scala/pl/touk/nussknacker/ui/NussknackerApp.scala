package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.config.DesignerConfigLoader
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

object NussknackerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      appFactory <- IO(new NussknackerAppFactory(getClass.getClassLoader))
      config     <- DesignerConfigLoader.load(getClass.getClassLoader)
      _          <- appFactory.createApp(config).use { _ => IO.never }
    } yield ExitCode.Success
  }

}
