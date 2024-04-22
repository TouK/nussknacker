package pl.touk.nussknacker.ui.api.description

import akka.http.scaladsl.model.StatusCode
import cats.Show
import derevo.circe._
import derevo.derive
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui._
import pl.touk.nussknacker.ui.api.TapirCodecs.ApiVersion._
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.migrate.{MigrationToArchivedError, MigrationValidationError}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, ApiVersion, OutOfRangeAdapterRequestError}
import sttp.model.StatusCode._
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class MigrationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import MigrationApiEndpoints.Dtos._
  import pl.touk.nussknacker.ui.api.TapirCodecs.MigrateScenarioRequestCodec._

  lazy val migrateEndpoint: SecuredEndpoint[MigrateScenarioRequestDto, MigrationError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Migration between environments service")
      .tag("Migrations")
      .post
      .in("migrate")
      .in(
        jsonBody[MigrateScenarioRequestDto].example(
          Example.of(
            summary = Some("Migrate given scenario to current Nu instance"),
            value = MigrateScenarioRequestDtoV2(
              version = 1,
              sourceEnvironmentId = "testEnv",
              remoteUserName = "testUser",
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
      .errorOut(migrateEndpointErrorOutput)
      .withSecurity(auth)

  lazy val scenarioDescriptionVersionEndpoint: SecuredEndpoint[Unit, Unit, ApiVersion, Any] =
    baseNuApiEndpoint
      .summary("current version of the scenario description version being used")
      .tag("Migrations")
      .get
      .in("migration" / "scenario" / "description" / "version")
      .out(jsonBody[ApiVersion])
      .withSecurity(auth)

  private val migrateEndpointErrorOutput: EndpointOutput.OneOf[MigrationError, MigrationError] =
    oneOf[MigrationError](
      oneOfVariant(
        BadRequest,
        plainBody[MigrationError.InvalidScenario]
      ),
      oneOfVariant(
        BadRequest,
        plainBody[MigrationError.CannotMigrateArchivedScenario]
      ),
      oneOfVariant(
        Unauthorized,
        plainBody[MigrationError.InsufficientPermission]
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
    sealed trait MigrationError

    object MigrationError {
      final case class InvalidScenario(errors: ValidationErrors) extends MigrationError
      final case class CannotMigrateArchivedScenario(processName: ProcessName, environment: String)
          extends MigrationError
      final case class InsufficientPermission(user: LoggedUser)                            extends MigrationError
      final case class MigrationApiAdapter(apiAdapterServiceError: ApiAdapterServiceError) extends MigrationError

      implicit val invalidScenarioErrorCodec: Codec[String, InvalidScenario, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, InvalidScenario](deserializationException)(validationError => {
            val errors = validationError.errors

            val messages = errors.globalErrors.map(_.error.message) ++
              errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
                s"$node - ${nerror.map(_.message).mkString(", ")}"
              }
            s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
          })
        )

      implicit val cannotMigrateArchivedScenarioErrorCodec
          : Codec[String, CannotMigrateArchivedScenario, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, CannotMigrateArchivedScenario](deserializationException)(migrationToArchived => {
            val processName = migrationToArchived.processName
            val environment = migrationToArchived.environment
            s"Cannot migrate, scenario $processName is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
          })
        )

      implicit val insufficientPermissionErrorCodec: Codec[String, InsufficientPermission, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, InsufficientPermission](deserializationException)(unauthorized => {
            val user = unauthorized.user

            s"The supplied user [${user.username}] is not authorized to access this resource"
          })
        )

    }

    def deserializationException =
      (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

    sealed trait MigrateScenarioRequestDto

    @derive(encoder, decoder)
    final case class MigrateScenarioRequestDtoV1(
        version: Int,
        sourceEnvironmentId: String,
        remoteUserName: String,
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean,
    ) extends MigrateScenarioRequestDto

    @derive(encoder, decoder)
    final case class MigrateScenarioRequestDtoV2(
        version: Int,
        sourceEnvironmentId: String,
        remoteUserName: String,
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean,
    ) extends MigrateScenarioRequestDto

  }

}
