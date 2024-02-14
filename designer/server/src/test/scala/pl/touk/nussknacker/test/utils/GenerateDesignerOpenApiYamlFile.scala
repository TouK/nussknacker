package pl.touk.nussknacker.test.utils

import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.nussknacker.ui.api.NuDesignerApiAvailableToExpose
import pl.touk.nussknacker.ui.util.Project

object GenerateDesignerOpenApiYamlFile extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] = for {
    outputFile <- determineOutputFileLocation(args)
    _          <- IO.delay(logger.info(s"Generating Nu Designer OpenAPI document (in ${outputFile.toString()}) ..."))
    _          <- generateAndSave(outputFile)
    _          <- IO.delay(logger.info("DONE!"))
  } yield ExitCode.Success

  private def determineOutputFileLocation(args: List[String]) = IO {
    args match {
      case Nil                   => defaultOutputFileLocation
      case customLocation :: Nil => File(customLocation)
      case _ => throw new IllegalArgumentException("Only one argument, the output file path, allowed!")
    }
  }

  private def generateAndSave(outputFile: File) = IO {
    outputFile
      .createFileIfNotExists(createParents = true)
      .overwrite(NuDesignerApiAvailableToExpose.generateOpenApiYaml)
  }

  private def defaultOutputFileLocation =
    Project.root / "docs-internal" / "api" / "nu-designer-openapi.yaml"

}
