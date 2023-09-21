package pl.touk.nussknacker.ui.util

import better.files.Dsl._
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.nussknacker.ui.services.NuDesignerApiAvailableToExpose

object GenerateDesignerOpenApiYamlFile extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] =
    generateAndSave()

  private def generateAndSave() = IO {
    logger.error("STARTING") // todo: remove

    val file = (pwd / "docs" / "api" / "internal" / "nu-designer-openapi.yaml")
      .createFileIfNotExists(createParents = true)

    val savedNuDesignerOpenApiYaml = file.contentAsString
    val newNuDesignerOpenApiYaml = NuDesignerApiAvailableToExpose.generateOpenApiYaml

    if (savedNuDesignerOpenApiYaml != newNuDesignerOpenApiYaml) {
      file.overwrite(newNuDesignerOpenApiYaml)
      logger.error(s"Content of ${file.path} has changed! Commit it manually and try to push again")
      ExitCode.Error
    } else {
      ExitCode.Success
    }
  }
}
