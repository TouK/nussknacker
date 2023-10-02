package pl.touk.nussknacker.ui.util

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.nussknacker.ui.services.NuDesignerApiAvailableToExpose

object GenerateDesignerOpenApiYamlFile extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- IO.delay(logger.info("Generating Nu Designer OpenAPI document ..."))
    _ <- generateAndSave()
    _ <- IO.delay(logger.info("DONE!"))
  } yield ExitCode.Success

  private def generateAndSave() = IO {
    (Project.root / "docs-internal" / "api" / "nu-designer-openapi.yaml")
      .createFileIfNotExists(createParents = true)
      .overwrite(NuDesignerApiAvailableToExpose.generateOpenApiYaml)
  }

}
