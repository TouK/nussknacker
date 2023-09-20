package pl.touk.nussknacker.ui.util

import better.files.Dsl._
import cats.effect.{ExitCode, IO, IOApp}
import pl.touk.nussknacker.ui.api.NuDesignerAvailableToExposeApi

object GenerateDesignerOpenApiYamlFile extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    generateAndSave()

  private def generateAndSave() = IO {
    val file = (pwd / "docs" / "api" / "internal" / "nu-designer-openapi.yaml")
      .createFileIfNotExists(createParents = true)

    val savedNuDesignerOpenApiYaml = file.contentAsString
    val newNuDesignerOpenApiYaml = NuDesignerAvailableToExposeApi.generateOpenApiYaml

    if (savedNuDesignerOpenApiYaml != newNuDesignerOpenApiYaml) {
      file.overwrite(newNuDesignerOpenApiYaml)
      ExitCode.Error
    } else {
      ExitCode.Success
    }
  }
}
