package pl.touk.nussknacker.dev

import cats.effect.{ExitCode, IO, IOApp}
import com.dimafeng.testcontainers.{DockerComposeContainer, WaitingForService}
import com.typesafe.scalalogging.LazyLogging
import org.testcontainers.containers.wait.strategy.ShellStrategy
import pl.touk.nussknacker.dev.RunEnvForLocalDesigner.Config.ScalaV
import scopt.{OParser, Read}

import java.io.{File => JFile}

// You can use it for a development purposes. It runs docker compose defined in `examples/dev` folder.
// After running this class you can run Nu Designer locally that can connect to the exposed services.
object RunEnvForLocalDesigner extends IOApp with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = for {
    config <- readConfig(args)
    _ <- log(s"Starting docker compose-based stack (for ${config.scalaV}) to be used with locally run Nu Designer...")
    _ <- createDockerEnv(config)
    _ <- log("You can run designer now...")
    _ <- IO.never[Unit]
  } yield ExitCode.Success

  private def readConfig(args: List[String]) = IO.delay {
    OParser
      .parse(Config.parser, args, Config())
      .getOrElse(throw new Exception("Invalid arguments"))
  }

  private def createDockerEnv(config: Config) = IO.delay {
    val scalaVOverrideYmlFile = config.scalaV match {
      case ScalaV.Scala212 => None
      case ScalaV.Scala213 => Some(new JFile("examples/dev/nu-scala213.override.yml"))
    }
    val env = new LocalTestingEnvDockerCompose(
      dockerComposeTweakFiles = scalaVOverrideYmlFile.toList ::: config.customizeYaml.toList
    )
    env.start()
    env
  }

  private def log(message: => String) = IO.delay(logger.info(message))

  final case class Config(scalaV: ScalaV = ScalaV.Scala213, customizeYaml: Option[JFile] = None)

  object Config {

    sealed trait ScalaV

    object ScalaV {
      case object Scala212 extends ScalaV
      case object Scala213 extends ScalaV

      implicit val scalaVRead: Read[ScalaV] =
        scopt.Read.reads(_.toLowerCase).map {
          case "scala212" => ScalaV.Scala212
          case "scala213" => ScalaV.Scala213
        }

    }

    private val builder = OParser.builder[Config]

    import builder._

    lazy val parser: OParser[Unit, Config] = OParser.sequence(
      head("Env for local development of Nu Designer"),
      programName("sbt designer/test:runMain pl.touk.nussknacker.dev.RunEnvForLocalDesigner"),
      opt[ScalaV]('s', "scalaV")
        .optional()
        .action((scalaV, c) => c.copy(scalaV = scalaV))
        .text("Scala version. Available options: scala212, scala213"),
      opt[JFile]('c', "customizeYaml")
        .optional()
        .valueName("<absolute file path>")
        .validate { file =>
          if (!file.exists()) Left(s"'$file' does NOT exist")
          else if (!file.isFile) Left(s"'$file' is NOT a file")
          else if (!file.canRead) Left(s"CANNOT read the file '$file'")
          else Right(())
        }
        .action((customizeYaml, c) => c.copy(customizeYaml = Some(customizeYaml)))
        .text("Yaml file for docker compose override"),
    )

  }

  class LocalTestingEnvDockerCompose(dockerComposeTweakFiles: Iterable[JFile])
      extends DockerComposeContainer(
        composeFiles = new JFile("examples/dev/local-testing.docker-compose.yml") ::
          dockerComposeTweakFiles.toList,
        waitingFor = Some(
          WaitingForService("wait-for-all", new ShellStrategy().withCommand("pwd")),
        ),
        // Change to 'true' to enable logging
        tailChildContainers = false
      )

}
