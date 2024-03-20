package pl.touk.nussknacker.ui.api

import derevo.circe._
import derevo.derive
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui._
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.migrate.{MigrationToArchivedError, MigrationValidationError}
import sttp.model.StatusCode._
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class MigrationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import MigrationApiEndpoints.Dtos.MigrateScenarioRequest._
  import MigrationApiEndpoints.Dtos._
  import pl.touk.nussknacker.ui.api.TapirCodecs.MigrateScenarioRequestCodec._

  lazy val migrateEndpoint: SecuredEndpoint[MigrateScenarioRequest, NuDesignerError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Migration between environments service")
      .tag("Migrations")
      .post
      .in("migrate")
      .in(
        jsonBody[MigrateScenarioRequest].example(
          Example.of(
            summary = Some("example of migration request between environments"),
            value = MigrateScenarioRequest(
              sourceEnvironmentId = "testEnv",
              processingMode = ProcessingMode.UnboundedStream,
              engineSetupName = EngineSetupName("Flink"),
              processCategory = "Category1",
              scenarioGraph = exampleGraph,
              processName = ProcessName("test"),
              isFragment = false
            )
          )
        )
      )
      .out(statusCode(Ok))
      .errorOut(nuDesignerErrorOutput)
      .withSecurity(auth)

  private val nuDesignerErrorOutput: EndpointOutput.OneOf[NuDesignerError, NuDesignerError] =
    oneOf[NuDesignerError](
      oneOfVariant(
        NotFound,
        plainBody[NotFoundError]
      ),
      oneOfVariant(
        BadRequest,
        plainBody[MigrationToArchivedError]
      ),
      oneOfVariant(
        BadRequest,
        plainBody[BadRequestError]
      ),
      oneOfVariant(
        BadRequest,
        plainBody[MigrationValidationError]
      ),
      oneOfVariant(
        Unauthorized,
        plainBody[UnauthorizedError]
      ),
      oneOfVariant(
        Conflict,
        plainBody[IllegalOperationError]
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[OtherError]
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[FatalError]
      )
    )

  private val exampleProcess = ScenarioBuilder
    .streamingLite("test")
    .source("source", "csv-source-lite")
    .emptySink("sink", "dead-end-lite")

  private val exampleGraph = CanonicalProcessConverter.toScenarioGraph(exampleProcess)

}

object MigrationApiEndpoints {

  object Dtos {

    def deserializationException =
      (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

    implicit val notFoundErrorCodec: Codec[String, NotFoundError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, NotFoundError](deserializationException)(_.getMessage)
      )

    implicit val migrationToArchivedErrorCodec: Codec[String, MigrationToArchivedError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, MigrationToArchivedError](deserializationException)(_.getMessage)
      )

    implicit val badRequestErrorCodec: Codec[String, BadRequestError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, BadRequestError](deserializationException)(_.getMessage)
      )

    implicit val unauthorizedErrorCodec: Codec[String, UnauthorizedError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, UnauthorizedError](deserializationException)(_.getMessage)
      )

    implicit val illegalOperationErrorCodec: Codec[String, IllegalOperationError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, IllegalOperationError](deserializationException)(_.getMessage)
      )

    implicit val otherErrorCodec: Codec[String, OtherError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, OtherError](deserializationException)(_.getMessage)
      )

    implicit val migrationValidationErrorCodec: Codec[String, MigrationValidationError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, MigrationValidationError](deserializationException)(_.getMessage)
      )

    implicit val fatalErrorCodec: Codec[String, FatalError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, FatalError](deserializationException)(_.getMessage)
      )

    @derive(encoder, decoder)
    final case class MigrateScenarioRequest(
        sourceEnvironmentId: String,
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean,
    )

  }

}
