package pl.touk.nussknacker.dev

import better.files.Resource
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment

import java.io.{File => JFile}

object RunEnvForLocalDesigner extends IOApp with LazyLogging {

  private lazy val dockerEnv = new DockerBasedInstallationExampleNuEnvironment(
    nussknackerImageVersion = "latest",
    dockerComposeTweakFiles = List(
      new JFile(Resource.getUrl("local-testing.override.yml").toURI),
    )
  )

  override def run(args: List[String]): IO[ExitCode] = for {
    _         <- log("Starting...")         // todo: better messages
    envClient <- IO.delay(dockerEnv.client)
    _         <- log("Run designer now...") // todo: better messages
    _         <- IO.never[Unit]
  } yield ExitCode.Success

  private def log(message: => String) = IO.delay(logger.info(message))
}
