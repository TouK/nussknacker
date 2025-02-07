package pl.touk.nussknacker.ui

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.config.AlwaysLoadingFileBasedDesignerConfigLoader
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory

object NussknackerApp extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    program.useForever
      .handleErrorWith((t: Throwable) => {
        IO {
          logger.error("Application failed", t)
        }
      })
      .as(ExitCode.Success)
  }

  private def program = for {
    appFactory <- NussknackerAppFactory.create(AlwaysLoadingFileBasedDesignerConfigLoader(getClass.getClassLoader))
    _          <- appFactory.createApp()
  } yield ()

}
