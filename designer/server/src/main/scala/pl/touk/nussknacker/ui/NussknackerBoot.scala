package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.factory.NussknackerApp

object NussknackerBoot extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      app <- IO(new NussknackerApp())
      _ <- app.init().use { _ => IO.never }
    } yield ExitCode.Success
  }
}
