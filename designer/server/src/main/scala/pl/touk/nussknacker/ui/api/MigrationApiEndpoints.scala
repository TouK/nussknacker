package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui._
import sttp.model.StatusCode._
import sttp.tapir.EndpointIO.Example
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

class MigrationApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import MigrationApiEndpoints.Dtos._
  import TapirCodecs.ScenarioNameCodec._
  import TapirCodecs.VersionIdCodec._

  lazy val migrateEndpoint: SecuredEndpoint[ScenarioWithDetailsForMigrations, NuDesignerError, Unit, Any] =
    baseNuApiEndpoint
      .summary("TODO")
      .tag("TODO")
      .post
      .in("migrate")
      .in(jsonBody[ScenarioWithDetailsForMigrations])
      .out(statusCode(Ok))
      .errorOut(nuDesignerErrorOutput)
      .withSecurity(auth)

  val nuDesignerErrorOutput: EndpointOutput.OneOf[NuDesignerError, NuDesignerError] =
    oneOf[NuDesignerError](
      oneOfVariant(
        NotFound,
        plainBody[NotFoundError]
          .example(
            Example.of(???)
          )
      ),
      oneOfVariant(
        BadRequest,
        plainBody[BadRequestError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        Unauthorized,
        plainBody[UnauthorizedError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        Conflict,
        plainBody[IllegalOperationError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[OtherError]
          .example(Example.of(???))
      ),
      oneOfVariant(
        InternalServerError,
        plainBody[FatalError]
          .example(Example.of(???))
      )
    )

}

object MigrationApiEndpoints {

  object Dtos {
    def deserializationException =
      (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

    implicit val notFoundErrorCodec: Codec[String, NotFoundError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, NotFoundError](deserializationException)(_.getMessage)
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

    implicit val fatalErrorCodec: Codec[String, FatalError, CodecFormat.TextPlain] =
      Codec.string.map(
        Mapping.from[String, FatalError](deserializationException)(_.getMessage)
      )

  }

  /*  implicit val scenarioGraphSchema: Schema[ScenarioGraph]                     = Schema.derived
  implicit val processPropertiesSchema: Schema[ProcessProperties]             = Schema.derived
  implicit val nodeDataSchema: Schema[NodeData]                               = Schema.derived
  implicit val processAdditionalFieldsSchema: Schema[ProcessAdditionalFields] = Schema.derived
  implicit val edgeSchema: Schema[Edge]                                       = Schema.derived
  implicit val parameterchema: Schema[Parameter]                              = Schema.derived
  implicit val edgeTypeSchema: Schema[EdgeType]                               = Schema.derived
  implicit val branchDefinitionSchema: Schema[BranchEndDefinition]            = Schema.derived
  implicit val userDefinedAdditionalNodeFieldSchema: Schema[UserDefinedAdditionalNodeFields] =
    Schema.derived
  implicit val expressionSchema: Schema[Expression]                       = Schema.derived
  implicit val serviceRefSchema: Schema[ServiceRef]                       = Schema.derived
  implicit val laoyoutDataSchema: Schema[LayoutData]                      = Schema.derived
  implicit val fragmentRefSchema: Schema[FragmentRef]                     = Schema.derived
  implicit val fragmentParameterSchema: Schema[FragmentParameter]         = Schema.derived
  implicit val fieldSchema: Schema[Field]                                 = Schema.derived
  implicit val fragmentClazzRef: Schema[FragmentClazzRef]                 = Schema.derived
  implicit val fragmentOutputDefinition: Schema[FragmentOutputDefinition] = Schema.derived
  implicit val fixedExpressionValueSchema: Schema[FixedExpressionValue]   = Schema.derived
  implicit val fragmentOutputVarDefinition: Schema[FragmentOutputVarDefinition] =
    Schema.derived
  implicit val branchParametersSchema: Schema[BranchParameters]                                       = Schema.derived
  implicit val valueInputWithFixedValuesSchema: Schema[ValueInputWithFixedValues]                     = Schema.derived
  implicit val sinkRefSchema: Schema[SinkRef]                                                         = Schema.derived
  implicit val parameterValueCompileTimeValidationSchema: Schema[ParameterValueCompileTimeValidation] = Schema.derived
  implicit val sourceRefSchema: Schema[SourceRef]                                                     = Schema.derived
  implicit val processNameSchema: Schema[ProcessName]                                                 = Schema.derived
  implicit val scenarioParametersSchema: Schema[ScenarioParameters]                                   = Schema.derived
  implicit val processingModeSchema: Schema[ProcessingMode]                                           = Schema.derived
  implicit val engineSetupNameSchema: Schema[EngineSetupName]                                         = Schema.derived
  implicit val scenarioWithDetailsSchema: Schema[ScenarioWithDetails] = Schema.derived
  implicit val processIdSchema: Schema[ProcessId] = Schema.derived
  implicit val versionIdSchema: Schema[VersionId] = Schema.derived
  implicit val s1: Schema[ProcessAction] = Schema.derived
  implicit val s2: Schema[ProcessActionId] = Schema.derived
  implicit val s3: Schema[pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult] = Schema.derived*/
}
