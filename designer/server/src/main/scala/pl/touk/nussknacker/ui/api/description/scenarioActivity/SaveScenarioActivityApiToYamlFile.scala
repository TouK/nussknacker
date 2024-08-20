package pl.touk.nussknacker.ui.api.description.scenarioActivity

import akka.actor.ActorSystem
import akka.stream.Materializer
import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import pl.touk.nussknacker.ui.server.{AkkaHttpBasedTapirStreamEndpointProvider, TapirStreamEndpointProvider}
import pl.touk.nussknacker.ui.util.Project
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

object SaveScenarioActivityApiToYamlFile extends IOApp with StrictLogging {

  override def run(args: List[String]): IO[ExitCode] = for {
    outputFile <- determineOutputFileLocation(args)
    _ <- IO.delay(logger.info(s"Generating Nu Scenario Activity OpenAPI document (in ${outputFile.toString()}) ..."))
    _ <- generateAndSave(outputFile)
    _ <- IO.delay(logger.info("DONE!"))
  } yield ExitCode.Success

  private def determineOutputFileLocation(args: List[String]) = IO {
    args match {
      case Nil                   => defaultOutputFileLocation
      case customLocation :: Nil => File(customLocation)
      case _ => throw new IllegalArgumentException("Only one argument, the output file path, allowed!")
    }
  }

  private def generateAndSave(outputFile: File) = {
    val actorSystem: ActorSystem = ActorSystem()
    val mat: Materializer        = Materializer(actorSystem)

    val streamProvider: TapirStreamEndpointProvider = new AkkaHttpBasedTapirStreamEndpointProvider()(mat)
    val docs = OpenAPIDocsInterpreter(Endpoints.openAPIOptions).toOpenAPI(
      es = Endpoints.apiEndpoints(streamProvider),
      title = Endpoints.apiDocumentTitle,
      version = Endpoints.apiVersion
    )

    outputFile
      .createFileIfNotExists(createParents = true)
      .overwrite(docs.toYaml)

    IO.fromFuture(IO.delay(actorSystem.terminate()))
  }

  private def defaultOutputFileLocation =
    Project.root / "docs-internal" / "api" / "nu-scenario-activity-openapi.yaml"

}
