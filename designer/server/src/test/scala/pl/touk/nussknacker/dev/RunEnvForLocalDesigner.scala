package pl.touk.nussknacker.dev

import better.files.Resource
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment

import java.io.{File => JFile}

object RunEnvForLocalDesigner extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = for {
    scalaV <- readScalaVersion(args)
    _      <- log("Starting...")         // todo: better messages
    _      <- createDockerEnv(scalaV)
    _      <- log("Run designer now...") // todo: better messages
    _      <- IO.never[Unit]
  } yield ExitCode.Success

  private def readScalaVersion(args: List[String]) = IO.delay {
    args.headOption.map(_.toLowerCase) match {
      case Some("scala212") => ScalaV.Scala212
      case Some("Scala213") => ScalaV.Scala213
      case Some(other) =>
        throw new IllegalArgumentException(s"[$other] Not supported Scala version. Use: scala212 or scala213")
      case None => ScalaV.Scala213
    }
  }

  private def createDockerEnv(scalaV: ScalaV) = IO.delay {
    val overrideYml = scalaV match {
      case ScalaV.Scala212 => "local-testing-scala212.override.yml"
      case ScalaV.Scala213 => "local-testing-scala213.override.yml"
    }
    val env = new DockerBasedInstallationExampleNuEnvironment(
      nussknackerImageVersion = "latest",
      dockerComposeTweakFiles = new JFile(Resource.getUrl(overrideYml).toURI) :: Nil
    )
    // we don't need this service (TBH designer is not needed too, but I left it intentionally - maybe it'd be useful)
    env.client.stopService("nginx")
    env
  }

  private def log(message: => String) = IO.delay(logger.info(message))

  sealed trait ScalaV

  object ScalaV {
    case object Scala212 extends ScalaV
    case object Scala213 extends ScalaV
  }

}
