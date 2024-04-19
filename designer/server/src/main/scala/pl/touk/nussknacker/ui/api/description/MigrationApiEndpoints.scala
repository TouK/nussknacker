package pl.touk.nussknacker.ui.api.description

import akka.http.scaladsl.model.StatusCode
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
            summary = Some("example of migration request between environments"),
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

  lazy val scenarioDescriptionVersionEndpoint: SecuredEndpoint[Unit, MigrationError, ApiVersion, Any] =
    baseNuApiEndpoint
      .summary("current version of the scenario description version being used")
      .tag("Migrations")
      .get
      .in("migration" / "scenario" / "description" / "version")
      .out(jsonBody[ApiVersion])
      .errorOut(scenarioDescriptionVersionEndpointErrorOutput)
      .withSecurity(auth)

  private val scenarioDescriptionVersionEndpointErrorOutput: EndpointOutput.OneOf[MigrationError, MigrationError] =
    oneOf[MigrationError](
      oneOfVariant(
        Unauthorized,
        plainBody[MigrationError.Unauthorized]
      )
    )

  private val migrateEndpointErrorOutput: EndpointOutput.OneOf[MigrationError, MigrationError] =
    oneOf[MigrationError](
      oneOfVariant(
        BadRequest,
        plainBody[MigrationError.Validation]
      ),
      oneOfVariant(
        BadRequest,
        plainBody[MigrationError.MigrationToArchived]
      ),
      oneOfVariant(
        Unauthorized,
        plainBody[MigrationError.Unauthorized]
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[MigrationError.MigrationApiAdapter]
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[MigrationError.Unknown]
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[Dtos.MigrationError.RemoteEnvironmentCommunicationError]
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
      final case class Validation(errors: ValidationErrors)                                extends MigrationError
      final case class MigrationToArchived(processName: ProcessName, environment: String)  extends MigrationError
      final case class Unauthorized(user: LoggedUser)                                      extends MigrationError
      final case class MigrationApiAdapter(apiAdapterServiceError: ApiAdapterServiceError) extends MigrationError

      implicit val validationErrorCodec: Codec[String, Validation, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, Validation](deserializationException)(validationError => {
            val errors = validationError.errors

            val messages = errors.globalErrors.map(_.error.message) ++
              errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
                s"$node - ${nerror.map(_.message).mkString(", ")}"
              }
            s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
          })
        )

      final case class RemoteEnvironmentCommunicationError(message: String) extends MigrationError

      final case class Unknown(message: String) extends MigrationError

      implicit val migrationToArchivedErrorCodec: Codec[String, MigrationToArchived, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, MigrationToArchived](deserializationException)(migrationToArchived => {
            val processName = migrationToArchived.processName
            val environment = migrationToArchived.environment
            s"Cannot migrate, scenario $processName is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
          })
        )

      implicit val unauthorizedErrorCodec: Codec[String, Unauthorized, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, Unauthorized](deserializationException)(unauthorized => {
            val user = unauthorized.user

            s"The supplied user [${user.username}] is not authorized to access this resource"
          })
        )

      implicit val migrationApiAdapterErrorCodec: Codec[String, MigrationApiAdapter, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, MigrationApiAdapter](deserializationException)(migrationApiAdapter => {
            val apiAdapterError = migrationApiAdapter.apiAdapterServiceError

            apiAdapterError match {
              case OutOfRangeAdapterRequestError(currentVersion, signedNoOfVersionsLeftToApply) =>
                signedNoOfVersionsLeftToApply match {
                  case n if n >= 0 =>
                    s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to $signedNoOfVersionsLeftToApply version(s) up"
                  case _ =>
                    s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to ${-signedNoOfVersionsLeftToApply} version(s) down"
                }
            }
          })
        )

      implicit val unknownErrorCodec: Codec[String, Unknown, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, Unknown](deserializationException)(unknown => {
            val message = unknown.message

            s"Unknown migration between environments error happened: $message"
          })
        )

      implicit val remoteEnvironmentCommunicationErrorCodec
          : Codec[String, RemoteEnvironmentCommunicationError, CodecFormat.TextPlain] =
        Codec.string.map(
          Mapping.from[String, RemoteEnvironmentCommunicationError](deserializationException)(
            remoteEnvironmentCommunicationError => {
              val message = remoteEnvironmentCommunicationError.message

              message
            }
          )
        )

    }

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
