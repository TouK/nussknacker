package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Codec, _}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.NodesError.{MalformedTypingResult, NoProcessingType, NoScenario}
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.suggester.CaretPosition2d
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.SchemaType.SString
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import scala.language.implicitConversions

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._

  lazy val nodesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, NodeData), NodesError, Option[AdditionalInfo], Any] = {
    baseNuApiEndpoint
      .summary("Additional info for provided node")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "additionalInfo")
      .in(jsonBody[NodeData])
      .out(
        statusCode(Ok).and(
          jsonBody[Option[AdditionalInfo]]
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  lazy val nodesValidationEndpoint
      : SecuredEndpoint[(ProcessName, NodeValidationRequestDto), NodesError, NodeValidationResultDto, Any] = {
    baseNuApiEndpoint
      .summary("Validate provided Node")
      .tag("Nodes")
      .post
      .in("nodes" / path[ProcessName]("scenarioName") / "validation")
      .in(jsonBody[NodeValidationRequestDto])
      .out(
        statusCode(Ok).and(
          jsonBody[NodeValidationResultDto]
        )
      )
      .errorOut(
        oneOf[NodesError](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoScenario]
              .example(
                Example.of(
                  summary = Some("No scenario {scenarioName} found"),
                  value = NoScenario(ProcessName("'example scenario'"))
                )
              )
          ),
          oneOfVariantFromMatchType(
            BadRequest,
            plainBody[MalformedTypingResult]
          )
        )
      )
      .withSecurity(auth)
  }

  lazy val propertiesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, ProcessProperties), NodesError, Option[AdditionalInfo], Any] = {
    baseNuApiEndpoint
      .summary("Additional info for provided properties")
      .tag("Nodes")
      .post
      .in("properties" / path[ProcessName]("scenarioName") / "additionalInfo")
      .in(jsonBody[ProcessProperties])
      .out(
        statusCode(Ok).and(
          jsonBody[Option[AdditionalInfo]]
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  lazy val propertiesValidationEndpoint
      : SecuredEndpoint[(ProcessName, PropertiesValidationRequestDto), NodesError, NodeValidationResultDto, Any] = {
    baseNuApiEndpoint
      .summary("Validate node properties")
      .tag("Nodes")
      .post
      .in("properties" / path[ProcessName]("scenarioName") / "validation")
      .in(jsonBody[PropertiesValidationRequestDto])
      .out(
        statusCode(Ok).and(
          jsonBody[NodeValidationResultDto]
        )
      )
      .errorOut(scenarioNotFoundErrorOutput)
      .withSecurity(auth)
  }

  lazy val parametersValidationEndpoint: SecuredEndpoint[
    (ProcessingType, ParametersValidationRequestDto),
    NodesError,
    ParametersValidationResultDto,
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Validate node parameters")
      .tag("Nodes")
      .post
      .in("parameters" / path[ProcessingType]("processingType") / "validate")
      .in(jsonBody[ParametersValidationRequestDto])
      .out(
        statusCode(Ok).and(
          jsonBody[ParametersValidationResultDto]
        )
      )
      .errorOut(
        oneOf[NodesError](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoProcessingType]
          ),
          oneOfVariantFromMatchType(
            BadRequest,
            plainBody[MalformedTypingResult]
          )
        )
      )
      .withSecurity(auth)
  }

  lazy val parametersSuggestionsEndpoint: SecuredEndpoint[
    (ProcessingType, ExpressionSuggestionRequestDto),
    NodesError,
    List[ExpressionSuggestionDto],
    Any
  ] = {
    baseNuApiEndpoint
      .summary("Suggest possible variables")
      .tag("Nodes")
      .post
      .in("parameters" / path[ProcessingType]("processingType") / "suggestions")
      .in(jsonBody[ExpressionSuggestionRequestDto])
      .out(
        statusCode(Ok).and(
          jsonBody[List[ExpressionSuggestionDto]]
        )
      )
      .errorOut(
        oneOf[NodesError](
          oneOfVariantFromMatchType(
            NotFound,
            plainBody[NoProcessingType]
          ),
          oneOfVariantFromMatchType(
            BadRequest,
            plainBody[MalformedTypingResult]
          )
        )
      )
      .withSecurity(auth)
  }

  private lazy val scenarioNotFoundErrorOutput: EndpointOutput.OneOf[NodesError, NodesError] =
    oneOf[NodesError](
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            )
          )
      )
    )

}

object NodesApiEndpoints {

  object Dtos {

    implicit lazy val parameterNameSchema: Schema[ParameterName] = Schema.string

    case class TypingResultInJson(value: Json)

    object TypingResultInJson {
      implicit def apply(typingResultInJson: TypingResultInJson): Json = typingResultInJson.value
      implicit lazy val typingResultInJsonDecoder: Decoder[TypingResultInJson] =
        Decoder.decodeJson.map(TypingResultInJson.apply)
      implicit lazy val typingResultInJsonEncoder: Encoder[TypingResultInJson] =
        Encoder.instance(typingResultInJson => typingResultInJson.value)
      implicit lazy val typingResultInJsonSchema: Schema[TypingResultInJson] = TypingDtoSchemas.typingResult.as
    }

    implicit lazy val scenarioNameSchema: Schema[ProcessName]                         = Schema.derived
    implicit lazy val additionalInfoSchema: Schema[AdditionalInfo]                    = Schema.derived
    implicit lazy val scenarioAdditionalFieldsSchema: Schema[ProcessAdditionalFields] = Schema.derived

    // Request doesn't need valid encoder
    @derive(decoder, schema)
    final case class NodeValidationRequestDto(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResultInJson],
        branchVariableTypes: Option[Map[String, Map[String, TypingResultInJson]]],
        outgoingEdges: Option[List[Edge]]
    )

    object NodeValidationRequestDto {
      implicit lazy val nodeDataSchema: Schema[NodeData]                    = Schema.anyObject
      implicit lazy val scenarioPropertiesSchema: Schema[ProcessProperties] = Schema.derived.hidden(true)
      implicit val nodeValidationRequestDtoEmptyEncoder: Encoder[NodeValidationRequestDto] =
        Encoder.encodeJson.contramap[NodeValidationRequestDto](_ => throw new IllegalStateException)
    }

    // Response doesn't need valid decoder
    @derive(encoder, schema)
    final case class NodeValidationResultDto(
        parameters: Option[List[UIParameterDto]],
        expressionType: Option[TypingResult],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    implicit val nodeValidationRequestDtoDecoder: Decoder[NodeValidationResultDto] =
      Decoder.instance[NodeValidationResultDto](_ => throw new IllegalStateException)

    object NodeValidationResultDto {

      def apply(node: NodeValidationResult): NodeValidationResultDto = {
        new NodeValidationResultDto(
          parameters = node.parameters.map { list =>
            list.map(param => UIParameterDto(param))
          },
          expressionType = node.expressionType,
          validationErrors = node.validationErrors,
          validationPerformed = node.validationPerformed
        )
      }

    }

    // Only used in response, no need for valid decoder
    @derive(encoder, schema)
    final case class UIParameterDto(
        name: String,
        typ: TypingResult,
        editor: ParameterEditor,
        defaultValue: Expression,
        additionalVariables: Map[String, TypingResult],
        variablesToHide: Set[String],
        branchParam: Boolean,
        hintText: Option[String],
        label: String
    )

    private object UIParameterDto {
      implicit lazy val parameterEditorSchema: Schema[ParameterEditor] = Schema.derived
      implicit lazy val dualEditorSchema: Schema[DualEditorMode]       = Schema.string

      implicit lazy val expressionSchema: Schema[Expression] = {
        implicit val languageSchema: Schema[Language] = Schema.string[Language]
        Schema.derived
      }

      implicit lazy val timeSchema: Schema[java.time.temporal.ChronoUnit] = Schema.anyObject

      def apply(param: UIParameter): UIParameterDto = new UIParameterDto(
        param.name,
        param.typ,
        param.editor,
        param.defaultValue,
        param.additionalVariables,
        param.variablesToHide,
        param.branchParam,
        param.hintText,
        param.label
      )

    }

    @derive(schema, encoder, decoder)
    final case class PropertiesValidationRequestDto(
        additionalFields: ProcessAdditionalFields,
        name: ProcessName
    )

    // Request doesn't need valid encoder
    @derive(schema, decoder)
    final case class ParametersValidationRequestDto(
        parameters: List[UIValueParameterDto],
        variableTypes: Map[String, TypingResultInJson]
    )

    implicit val parametersValidationRequestDtoEncoder: Encoder[ParametersValidationRequestDto] =
      Encoder.encodeJson.contramap[ParametersValidationRequestDto](_ => throw new IllegalStateException)

    // for a sake of generation Open API using Scala 2.12, we have to define it explicitly
    private implicit def listSchema[T: Schema]: Typeclass[List[T]] = Schema.schemaForIterable[T, List]

    @derive(schema, encoder, decoder)
    final case class ParametersValidationResultDto(
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    // Request doesn't need valid encoder
    @derive(schema, decoder)
    final case class UIValueParameterDto(
        name: String,
        typ: TypingResultInJson,
        expression: Expression
    )

    implicit lazy val expressionSchema: Schema[Expression] = {
      implicit val languageSchema: Schema[Language] = Schema.string[Language]
      Schema.derived
    }

    implicit lazy val caretPosition2dSchema: Schema[CaretPosition2d] = Schema.derived

    // Request doesn't need valid encoder
    @derive(schema, decoder)
    final case class ExpressionSuggestionRequestDto(
        expression: Expression,
        caretPosition2d: CaretPosition2d,
        variableTypes: Map[String, TypingResultInJson]
    )

    implicit val expressionSuggestionRequestDtoEncoder: Encoder[ExpressionSuggestionRequestDto] =
      Encoder.encodeJson.contramap[ExpressionSuggestionRequestDto](_ => throw new IllegalStateException)

    // Response doesn't need valid decoder
    @derive(schema, encoder)
    final case class ExpressionSuggestionDto(
        methodName: String,
        refClazz: TypingResult,
        fromClass: Boolean,
        description: Option[String],
        parameters: List[ParameterDto]
    )

    object ExpressionSuggestionDto {

      def apply(expr: ExpressionSuggestion): ExpressionSuggestionDto = {
        new ExpressionSuggestionDto(
          expr.methodName,
          expr.refClazz,
          expr.fromClass,
          expr.description,
          expr.parameters.map(param => ParameterDto(param.name, param.refClazz))
        )
      }

    }

    implicit val expressionSuggestionDtoDecoder: Decoder[ExpressionSuggestionDto] =
      Decoder.instance[ExpressionSuggestionDto](_ => throw new IllegalStateException)

    // Response doesn't need valid decoder
    @derive(schema, encoder)
    final case class ParameterDto(
        name: String,
        refClazz: TypingResult
    )

    def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
      new TypingResultDecoder(name =>
        ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
      ).decodeTypingResults
    }

    def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
      implicit val parameterNameDecoder: KeyDecoder[ParameterName] = KeyDecoder.decodeKeyString.map(ParameterName.apply)
      implicit val typeDecoder: Decoder[TypingResult]              = prepareTypingResultDecoder(modelData)
      implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
        deriveConfiguredDecoder[TestSourceParameters]
      deriveConfiguredDecoder[TestFromParametersRequest]
    }

    implicit val parameterNameCodec: KeyEncoder[ParameterName] = KeyEncoder.encodeKeyString.contramap(_.value)

    @JsonCodec(encodeOnly = true) final case class TestSourceParameters(
        sourceId: String,
        parameterExpressions: Map[ParameterName, Expression]
    )

    @JsonCodec(encodeOnly = true) final case class TestFromParametersRequest(
        sourceParameters: TestSourceParameters,
        scenarioGraph: ScenarioGraph
    )

    @JsonCodec(encodeOnly = true) final case class ParametersValidationRequest(
        parameters: List[UIValueParameter],
        variableTypes: Map[String, TypingResult]
    )

    @JsonCodec(encodeOnly = true) final case class NodeValidationResult(
        // It it used for node parameter adjustment on FE side (see ParametersUtils.ts -> adjustParameters)
        parameters: Option[List[UIParameter]],
        // expressionType is returned to present inferred types of a single, hardcoded parameter of the node
        // We currently support only type inference for an expression in the built-in components: variable and switch
        // and fields of the record-variable and fragment output (we return TypedObjectTypingResult in this case)
        // TODO: We should keep this in a map, instead of TypedObjectTypingResult as it is done in ValidationResult.typingInfo
        //       Thanks to that we could remove some code on the FE side and be closer to support also not built-in components
        expressionType: Option[TypingResult],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    @JsonCodec(encodeOnly = true) final case class NodeValidationRequest(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResult],
        branchVariableTypes: Option[Map[String, Map[String, TypingResult]]],
        // TODO: remove Option when FE is ready
        // In this request edges are not guaranteed to have the correct "from" field. Normally it's synced with node id but
        // when renaming node, it contains node's id before the rename.
        outgoingEdges: Option[List[Edge]]
    )

    def decodeVariableTypes(
        variableTypes: Map[String, Dtos.TypingResultInJson],
        typingResultDecoder: Decoder[TypingResult]
    ): Either[MalformedTypingResult, Map[String, TypingResult]] = {
      val result: Map[ProcessingType, TypingResult] = variableTypes
        .map { case (key, typingResult) =>
          (key, typingResultDecoder.decodeJson(typingResult))
        }
        .map { case (key, maybeValue) =>
          maybeValue match {
            case Left(failure: DecodingFailure) => return Left(MalformedTypingResult(failure.message))
            case Right(value)                   => (key, value)
          }
        }
      Right(result)
    }

    sealed trait NodesError

    object NodesError {
      final case class NoScenario(scenarioName: ProcessName)            extends NodesError
      final case class NoProcessingType(processingType: ProcessingType) extends NodesError
      final case object NoPermission                                    extends NodesError with CustomAuthorizationError
      final case class MalformedTypingResult(msg: String)               extends NodesError

      private def deserializationNotSupportedException =
        (ignored: Any) => throw new IllegalStateException("Deserializing errors is not supported.")

      implicit val noScenarioCodec: Codec[String, NoScenario, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoScenario](deserializationNotSupportedException)(e =>
            s"No scenario ${e.scenarioName} found"
          )
        )
      }

      implicit val noProcessingTypeCodec: Codec[String, NoProcessingType, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, NoProcessingType](deserializationNotSupportedException)(e =>
            s"ProcessingType type: ${e.processingType} not found"
          )
        )
      }

      implicit val malformedTypingResultCoded: Codec[String, MalformedTypingResult, CodecFormat.TextPlain] = {
        Codec.string.map(
          Mapping.from[String, MalformedTypingResult](deserializationNotSupportedException)(e =>
            s"The request content was malformed:\n${e.msg}"
          )
        )
      }

    }

  }

}

object TypingDtoSchemas {

  import pl.touk.nussknacker.engine.api.typed.TypingType
  import pl.touk.nussknacker.engine.api.typed.TypingType.TypingType
  import pl.touk.nussknacker.engine.api.typed.typing._
  import sttp.tapir.Schema.SName
  import sttp.tapir.SchemaType.SProductField
  import sttp.tapir.{FieldName, Schema, SchemaType}

  implicit lazy val typingResult: Schema[TypingResult] = Schema.derived

  implicit lazy val singleTypingResultSchema: Schema[SingleTypingResult] =
    Schema.derived.hidden(true)

  implicit lazy val additionalDataValueSchema: Schema[AdditionalDataValue] = Schema.derived

  implicit lazy val typedObjectTypingResultSchema: Schema[TypedObjectTypingResult] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(
            SProductField[String, Map[String, TypingResult]](
              FieldName("fields"),
              Schema.schemaForMap[TypingResult],
              _ => None
            )
          ) :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectTypingResult"))
    )
      .title("TypedObjectTypingResult")
      .as
  }

  implicit lazy val typedDictSchema: Schema[TypedDict] = {
    final case class Dict(id: String, valueType: TypedTaggedValue)
    lazy val dictSchema: Schema[Dict] = Schema.derived
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(SProductField[String, Dict](FieldName("dict"), dictSchema, _ => None))
      ),
      Some(SName("TypedDict"))
    )
      .title("TypedDict")
      .as
  }

  implicit lazy val typedObjectWithDataSchema: Schema[TypedObjectWithData] =
    Schema.derived.hidden(true)

  implicit lazy val typedTaggedSchema: Schema[TypedTaggedValue] = {
    Schema(
      SchemaType.SProduct(
        List(SProductField[String, String](FieldName("tag"), Schema.string, tag => Some(tag))) :::
          sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedTaggedValue"))
    )
      .title("TypedTaggedValue")
      .as
  }

  implicit lazy val typedObjectSchema: Schema[TypedObjectWithValue] = {
    Schema(
      SchemaType.SProduct(
        List(SProductField[String, Any](FieldName("value"), Schema.any, value => Some(value))) :::
          sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedObjectWithValue"))
    )
      .title("TypedObjectWithValue")
      .as
  }

  implicit lazy val typedNullSchema: Schema[TypedNull.type] =
    Schema.derived.name(Schema.SName("TypedNull")).title("TypedNull")

  implicit lazy val unknownSchema: Schema[Unknown.type] =
    Schema.derived
      .name(Schema.SName("Unknown"))
      .title("Unknown")

  implicit lazy val typedUnionSchema: Schema[TypedUnion] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          List(
            SProductField[String, List[TypingResult]](
              FieldName("union"),
              Schema.schemaForArray[TypingResult].as,
              _ => Some(List(Unknown))
            )
          )
      ),
      Some(Schema.SName("TypedUnion"))
    )
      .title("TypedUnion")
      .as
  }

  implicit lazy val typedClassSchema: Schema[TypedClass] = {
    Schema(
      SchemaType.SProduct(
        sProductFieldForDisplayAndType :::
          sProductFieldForKlassAndParams
      ),
      Some(SName("TypedClass"))
    )
      .title("TypedClass")
      .as
  }

  private lazy val sProductFieldForDisplayAndType: List[SProductField[String]] = {
    List(
      SProductField[String, String](
        FieldName("display"),
        Schema(SString(), isOptional = true),
        display => Some(display)
      ),
      SProductField[String, TypingType](
        FieldName("type"),
        Schema.derivedEnumerationValue,
        _ => Some(TypingType.Unknown)
      )
    )
  }

  private lazy val sProductFieldForKlassAndParams: List[SProductField[String]] = {
    lazy val typingResultSchema: Schema[TypingResult] = Schema.derived
    List(
      SProductField[String, String](FieldName("refClazzName"), Schema.string, refClazzName => Some(refClazzName)),
      SProductField[String, List[TypingResult]](
        FieldName("params"),
        Schema.schemaForIterable[TypingResult, List](typingResultSchema),
        _ => Some(List(Unknown))
      )
    )
  }

}
