package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

object NussknackerApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val program = for {
      appFactory <- NussknackerAppFactory.create(getClass.getClassLoader)
      app        <- appFactory.createApp()
    } yield app
    program.useForever.as(ExitCode.Success)
  }

}
