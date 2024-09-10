package pl.touk.nussknacker.ui.api.description

import cats.Show
import derevo.circe._
import derevo.derive
import io.circe.{Decoder, Encoder}
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  UIGlobalError,
  ValidationErrors
}
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  MigrateScenarioRequestDto,
  MigrateScenarioRequestDtoV1,
  MigrateScenarioRequestDtoV2
}
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationError
import pl.touk.nussknacker.ui.migrations.MigrationService.MigrationError.{
  CannotMigrateArchivedScenario,
  CannotTransformMigrateScenarioRequestIntoMigrationDomain,
  InsufficientPermission,
  InvalidScenario
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.security.api.RealLoggedUser
import sttp.model.StatusCode._
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class MigrationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import MigrationApiEndpoints.Codecs._
  import MigrationApiEndpoints.Codecs.MigrateScenarioRequestDto._
  import MigrationApiEndpoints.Codecs.MigrateScenarioRequestDto.schema
  import MigrationApiEndpoints.Dtos._

  lazy val migrateEndpoint: SecuredEndpoint[MigrateScenarioRequestDto, MigrationError, Unit, Any] =
    baseNuApiEndpoint
      .summary("Migration between environments service")
      .tag("Migrations")
      .post
      .in("migrate")
      .in(
        jsonBody[MigrateScenarioRequestDto]
        // FIXME uncomment examples when discriminator validation will work in the NuDesignerApiAvailableToExposeYamlSpec -
        // currently when examples are given, the validation in tests fails due to two schemas matching the example json
//          .examples(
//            List(
//              Example.of(
//                summary = Some("Migrate given scenario from version 2 to current Nu instance"),
//                value = MigrateScenarioRequestDtoV2(
//                  version = 2,
//                  sourceEnvironmentId = "testEnv",
//                  remoteUserName = "testUser",
//                  processingMode = ProcessingMode.UnboundedStream,
//                  engineSetupName = EngineSetupName("Flink"),
//                  processCategory = "Category1",
//                  scenarioLabels = List("tag1", "tag2"),
//                  scenarioGraph = exampleGraph,
//                  processName = ProcessName("test"),
//                  isFragment = false
//                )
//              ),
//              Example.of(
//                summary = Some("Migrate given scenario from version 1 to current Nu instance"),
//                value = MigrateScenarioRequestDtoV1(
//                  version = 1,
//                  sourceEnvironmentId = "testEnv",
//                  remoteUserName = "testUser",
//                  processingMode = ProcessingMode.UnboundedStream,
//                  engineSetupName = EngineSetupName("Flink"),
//                  processCategory = "Category1",
//                  scenarioGraph = exampleGraph,
//                  processName = ProcessName("test"),
//                  isFragment = false
//                )
//              )
//            )
//          )
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

  private val migrateEndpointErrorOutput =
    oneOf[MigrationError](
      oneOfVariant(
        BadRequest,
        migrationErrorPlainBody[InvalidScenario]
          .example(
            Example.of(
              summary = Some("Invalid scenario to migrate"),
              value = InvalidScenario(
                ValidationErrors(
                  Map.empty,
                  List.empty,
                  List(
                    UIGlobalError(
                      NodeValidationError(
                        "FragmentParamClassLoadError",
                        "Invalid parameter type.",
                        "Failed to load i.do.not.exist",
                        Some("$param.badParam.$typ"),
                        NodeValidationErrorType.SaveAllowed,
                        None
                      ),
                      List("node1")
                    )
                  )
                )
              )
            )
          )
      ),
      oneOfVariant(
        BadRequest,
        migrationErrorPlainBody[CannotMigrateArchivedScenario]
          .example(
            Example.of(
              summary = Some("Attempt to migrate scenario which is already archived"),
              value = CannotMigrateArchivedScenario(ProcessName("process1"), "test")
            )
          )
      ),
      oneOfVariant(
        Unauthorized,
        migrationErrorPlainBody[InsufficientPermission]
          .example(
            Example.of(
              summary = Some("Migration performed by user without sufficient permissions"),
              value = InsufficientPermission(RealLoggedUser("Peter", "Griffin"))
            )
          )
      ),
      oneOfVariant(
        BadRequest,
        migrationErrorPlainBody[CannotTransformMigrateScenarioRequestIntoMigrationDomain.type]
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

    @derive(encoder, decoder, schema)
    final case class ApiVersion(version: Int)

    sealed trait MigrateScenarioRequestDto {
      def version: Int
    }

    @derive(encoder, decoder)
    final case class MigrateScenarioRequestDtoV1(
        override val version: Int,
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
        override val version: Int,
        sourceEnvironmentId: String,
        remoteUserName: String,
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioLabels: List[String],
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean,
    ) extends MigrateScenarioRequestDto

    /*
    NOTE TO DEVELOPER:

    When implementing MigrateScenarioRequestDtoV3:

    1. Review and update the parameter types and names if necessary.
    2. Consider backward compatibility with existing code.
    3. Update the encoder and decoder accordingly.
    4. Check if any adapters or converters need modification.
    5. Add any necessary documentation or comments.
    6. Update StandardRemoteEnvironmentSpec, especially the Migrate endpoint mock

    Remember to uncomment the class definition after implementation.

    @derive(encoder, decoder)
    final case class MigrateScenarioRequestDtoV3(
        override val version: Int,
        sourceEnvironmentId: String,
        remoteUserName: String,
        processingMode: ProcessingMode,
        engineSetupName: EngineSetupName,
        processCategory: String,
        scenarioLabels: List[String],
        scenarioGraph: ScenarioGraph,
        processName: ProcessName,
        isFragment: Boolean,
    ) extends MigrateScenarioRequestDto*/

  }

  object Codecs {

    implicit val migrationErrorShow: Show[MigrationError] = Show.show {
      case InvalidScenario(errors) =>
        val messages = errors.globalErrors.map(_.error.message) ++
          errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
            s"$node - ${nerror.map(_.message).mkString(", ")}"
          }
        s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
      case CannotMigrateArchivedScenario(processName, environment) =>
        s"Cannot migrate, scenario $processName is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
      case InsufficientPermission(user) =>
        s"The supplied user [${user.username}] is not authorized to access this resource"
      case CannotTransformMigrateScenarioRequestIntoMigrationDomain =>
        s"Error occurred while transforming migrate scenario request into domain object"
    }

    def migrationErrorPlainBody[T <: MigrationError](
        implicit showEv: Show[MigrationError]
    ): EndpointIO.Body[String, T] = {
      implicit val codec: Codec[String, T, CodecFormat.TextPlain] =
        BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[T](showEv.show)
      plainBody[T]
    }

    object MigrateScenarioRequestDto {

      implicit val schema: Schema[MigrateScenarioRequestDto] = {
        import pl.touk.nussknacker.ui.api.TapirCodecs.ProcessingModeCodec._
        import pl.touk.nussknacker.ui.api.TapirCodecs.EngineSetupNameCodec._
        import pl.touk.nussknacker.ui.api.TapirCodecs.ScenarioGraphCodec._
        import pl.touk.nussknacker.ui.api.TapirCodecs.ProcessNameCodec._
        implicit val migrateScenarioRequestV1Schema: Schema[MigrateScenarioRequestDtoV1] = Schema.derived
        implicit val migrateScenarioRequestV2Schema: Schema[MigrateScenarioRequestDtoV2] = Schema.derived

        val derived = Schema.derived[MigrateScenarioRequestDto]
        derived.schemaType match {
          case s: SchemaType.SCoproduct[_] =>
            derived.copy(schemaType =
              s.addDiscriminatorField(
                FieldName("version"),
                Schema.schemaForInt,
                Map(
                  "1" -> SchemaType.SRef(Schema.SName(classOf[MigrateScenarioRequestDtoV1].getSimpleName)),
                  "2" -> SchemaType.SRef(Schema.SName(classOf[MigrateScenarioRequestDtoV2].getSimpleName)),
                )
              )
            )
          case _ =>
            throw new IllegalStateException("Unexpected schema type")
        }
      }

      implicit val encoder: Encoder[MigrateScenarioRequestDto] = Encoder.instance {
        case v1: MigrateScenarioRequestDtoV1 => v1.asJson
        case v2: MigrateScenarioRequestDtoV2 => v2.asJson
      }

      implicit val decoder: Decoder[MigrateScenarioRequestDto] = Decoder.instance { cursor =>
        cursor.downField("version").as[Int].flatMap {
          case 1     => cursor.as[MigrateScenarioRequestDtoV1]
          case 2     => cursor.as[MigrateScenarioRequestDtoV2]
          case other => throw new IllegalStateException(s"Cannot decode migration request for version [$other]")
        }
      }

    }

  }

}
