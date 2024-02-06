package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.CirceUtil._
import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ProcessProperties
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.NodeValidationRequestDto
import pl.touk.nussknacker.ui.api.typingDtoSchemas._
import pl.touk.nussknacker.ui.suggester.CaretPosition2d
import sttp.model.StatusCode.{BadRequest, NotFound, Ok}
import sttp.tapir._
import TapirCodecs.ScenarioNameCodec._
import pl.touk.nussknacker.engine.spel.ExpressionSuggestion
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import scala.language.implicitConversions

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._

  lazy val nodesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, NodeData), String, Option[AdditionalInfo], Any] = {
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
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

  lazy val nodesValidationEndpoint
      : SecuredEndpoint[(ProcessName, NodeValidationRequestDto), String, NodeValidationResultDto, Any] = {
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
      // Todo: bad request code if wrong typing result sent
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

  lazy val propertiesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, ProcessProperties), String, Option[AdditionalInfo], Any] = {
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
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

  lazy val propertiesValidationEndpoint
      : SecuredEndpoint[(ProcessName, PropertiesValidationRequestDto), String, NodeValidationResultDto, Any] = {
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
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

  lazy val parametersValidationEndpoint: SecuredEndpoint[
    (ProcessingType, ParametersValidationRequestDto),
    String,
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
      // Todo: bad request code if wrong typing result sent
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

  lazy val parametersSuggestionsEndpoint: SecuredEndpoint[
    (ProcessingType, ExpressionSuggestionRequestDto),
    String,
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
      // Todo: bad request code if wrong typing result sent
      .errorOut(
        statusCode(NotFound).and(
          stringBody
        )
      )
      .withSecurity(auth)
  }

}

object NodesApiEndpoints {

  object Dtos {

    case class TypingResultInJson(value: Json)

    object TypingResultInJson {
      implicit def apply(typingResultInJson: TypingResultInJson): Json = typingResultInJson.value
      implicit lazy val typingResultInJsonDecoder: Decoder[TypingResultInJson] =
        Decoder.decodeJson.map(TypingResultInJson.apply)
      implicit lazy val typingResultInJsonEncoder: Encoder[TypingResultInJson] =
        Encoder.instance(typingResultInJson => typingResultInJson.value)
      implicit lazy val typingResultInJsonSchema: Schema[TypingResultInJson] = typingDtoSchemas.typingResult.as
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
        Encoder.encodeJson.contramap[NodeValidationRequestDto](_ => Json.Null)
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
      Decoder.instance[NodeValidationResultDto](_ => ???)

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

    object UIParameterDto {
      implicit lazy val parameterEditorSchema: Schema[ParameterEditor]    = Schema.derived
      implicit lazy val dualEditorSchema: Schema[DualEditorMode]          = Schema.string
      implicit lazy val expressionSchema: Schema[Expression]              = Schema.derived
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
      Encoder.encodeJson.contramap[ParametersValidationRequestDto](_ => Json.Null)

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

    implicit lazy val expressionSchema: Schema[Expression]           = Schema.derived
    implicit lazy val caretPosition2dSchema: Schema[CaretPosition2d] = Schema.derived

    // Request doesn't need valid encoder
    @derive(schema, decoder)
    final case class ExpressionSuggestionRequestDto(
        expression: Expression,
        caretPosition2d: CaretPosition2d,
        variableTypes: Map[String, TypingResultInJson]
    )

    implicit val expressionSuggestionRequestDtoEncoder: Encoder[ExpressionSuggestionRequestDto] =
      Encoder.encodeJson.contramap[ExpressionSuggestionRequestDto](_ => Json.Null)

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
      Decoder.instance[ExpressionSuggestionDto](_ => ???)

    // Response doesn't need valid decoder
    @derive(schema, encoder)
    final case class ParameterDto(
        name: String,
        refClazz: TypingResult
    )

    //    Things copied from NodesResource
    //    These 2 are used in ManagementResources for example

    def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
      new TypingResultDecoder(name =>
        ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
      ).decodeTypingResults
    }

    def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
        deriveConfiguredDecoder[TestSourceParameters]
      deriveConfiguredDecoder[TestFromParametersRequest]
    }

    //    These 3 doesn't look as used anywhere

    def prepareNodeRequestDecoder(modelData: ModelData): Decoder[NodeValidationRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      deriveConfiguredDecoder[NodeValidationRequest]
    }

    def prepareParametersValidationDecoder(modelData: ModelData): Decoder[ParametersValidationRequest] = {
      implicit val typeDecoder: Decoder[TypingResult]                 = prepareTypingResultDecoder(modelData)
      implicit val uiValueParameterDecoder: Decoder[UIValueParameter] = deriveConfiguredDecoder[UIValueParameter]
      deriveConfiguredDecoder[ParametersValidationRequest]
    }

    def preparePropertiesRequestDecoder(modelData: ModelData): Decoder[PropertiesValidationRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      deriveConfiguredDecoder[PropertiesValidationRequest]
    }

  }

  @JsonCodec(encodeOnly = true) final case class TestSourceParameters(
      sourceId: String,
      parameterExpressions: Map[String, Expression]
  )

  @JsonCodec(encodeOnly = true) final case class TestFromParametersRequest(
      sourceParameters: TestSourceParameters,
      scenarioGraph: ScenarioGraph
  )

  @JsonCodec(encodeOnly = true) final case class ParametersValidationResult(
      validationErrors: List[NodeValidationError],
      validationPerformed: Boolean
  )

  @JsonCodec(encodeOnly = true) final case class ParametersValidationRequest(
      parameters: List[UIValueParameter],
      variableTypes: Map[String, TypingResult]
  )

  object ParametersValidationRequest {
    import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.ParametersValidationRequestDto

    def apply(
        request: ParametersValidationRequestDto
    )(typingResultDecoder: Decoder[TypingResult]): ParametersValidationRequest = {
      new ParametersValidationRequest(
        request.parameters.map { parameter =>
          UIValueParameter(
            name = parameter.name,
            typ = typingResultDecoder
              .decodeJson(parameter.typ) match {
              case Left(failure)       => throw failure
              case Right(typingResult) => typingResult
            },
            expression = parameter.expression
          )
        },
        mapVariableTypesOrThrowError(request.variableTypes, typingResultDecoder)
      )
    }

  }

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

  object NodeValidationRequest {

    def apply(node: NodeValidationRequestDto)(typingResultDecoder: Decoder[TypingResult]): NodeValidationRequest = {
      new NodeValidationRequest(
        nodeData = node.nodeData,
        processProperties = node.processProperties,
        variableTypes = mapVariableTypesOrThrowError(node.variableTypes, typingResultDecoder),
        branchVariableTypes = node.branchVariableTypes.map { outerMap =>
          outerMap.map { case (name, innerMap) =>
            val changedMap = mapVariableTypesOrThrowError(innerMap, typingResultDecoder)
            (name, changedMap)
          }
        },
        outgoingEdges = node.outgoingEdges
      )

    }

  }

  def mapVariableTypesOrThrowError(
      variableTypes: Map[String, Dtos.TypingResultInJson],
      typingResultDecoder: Decoder[TypingResult]
  ): Map[String, TypingResult] = {
    variableTypes.map { case (key, typingResult) =>
      typingResultDecoder.decodeJson(typingResult) match {
        case Left(failure) => throw failure
        case Right(result) => (key, result)
      }
    }
  }

  @JsonCodec(encodeOnly = true) final case class PropertiesValidationRequest(
      additionalFields: ProcessAdditionalFields,
      name: ProcessName
  )

  final case class ExpressionSuggestionRequest(
      expression: Expression,
      caretPosition2d: CaretPosition2d,
      variableTypes: Map[String, TypingResult]
  )

  object ExpressionSuggestionRequest {

    implicit def decoder(implicit typing: Decoder[TypingResult]): Decoder[ExpressionSuggestionRequest] = {
      deriveConfiguredDecoder[ExpressionSuggestionRequest]
    }

  }

}
