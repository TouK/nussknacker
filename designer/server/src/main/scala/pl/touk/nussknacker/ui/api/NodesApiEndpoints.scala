package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.CirceUtil._
import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, Encoder, Json}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ProcessProperties
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.TypingType.TypedUnion
import pl.touk.nussknacker.engine.api.typed.{SimpleObjectEncoder, TypingResultDecoder, typing}
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
import pl.touk.nussknacker.ui.api.typingDtos._
import pl.touk.nussknacker.ui.suggester.CaretPosition2d
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import scala.language.implicitConversions

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._
  import NodesApiEndpoints.Dtos.ScenarioNameCodec._

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
    import typingDtoSchemas._

    object ScenarioNameCodec {
      def encode(scenarioName: ProcessName): String = scenarioName.value

      def decode(s: String): DecodeResult[ProcessName] = {
        val scenarioName = ProcessName.apply(s)
        DecodeResult.Value(scenarioName)
      }

      implicit val scenarioNameCodec: PlainCodec[ProcessName] = Codec.string.mapDecode(decode)(encode)
    }

    implicit lazy val scenarioNameSchema: Schema[ProcessName]                         = Schema.derived
    implicit lazy val additionalInfoSchema: Schema[AdditionalInfo]                    = Schema.derived
    implicit lazy val scenarioAdditionalFieldsSchema: Schema[ProcessAdditionalFields] = Schema.derived

    // wydaje mi się, że do requestu to encoder to nie jest potrzebny
    @derive(decoder, schema)
    final case class NodeValidationRequestDto(
        nodeData: NodeData,
        processProperties: ProcessProperties,
//        variableTypes: Map[String, TypingResultDto],
        variableTypes: Map[String, Json],
//        variableTypes: Json,
//        branchVariableTypes: Option[Map[String, Map[String, TypingResultDto]]],
        branchVariableTypes: Option[Map[String, Map[String, Json]]],
//        branchVariableTypes: Option[Json],
        outgoingEdges: Option[List[Edge]]
    )

    implicit val nodeValidationRequestDtoEmptyEncoder: Encoder[NodeValidationRequestDto] =
      Encoder.encodeJson.contramap[NodeValidationRequestDto](_ => Json.Null)
//    implicit val nodeValidationRequestDtoEmptyEncoder: Encoder[NodeValidationRequestDto] = ???

    object NodeValidationRequestDto {
      implicit lazy val nodeDataSchema: Schema[NodeData]                    = Schema.anyObject
      implicit lazy val scenarioPropertiesSchema: Schema[ProcessProperties] = Schema.derived.hidden(true)
    }

    // wydaje mi się, że do response decoder nie jest potrzebny
    @derive(encoder, schema)
    final case class NodeValidationResultDto(
        parameters: Option[List[UIParameterDto]],
        expressionType: Option[TypingResult],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    implicit val nodeValidationRequestDtoDecoder: Decoder[NodeValidationResultDto] =
      Decoder.instance[NodeValidationResultDto](_ => ???)
//    implicit val nodeValidationRequestDtoDecoder: Decoder[NodeValidationResultDto] = ???

    object NodeValidationResultDto {
//      import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDtoHelpers.toDto

      def apply(node: NodeValidationResult): NodeValidationResultDto = {
        new NodeValidationResultDto(
          parameters = node.parameters.map { list =>
            list.map { param =>
              UIParameterDto(
                name = param.name,
                typ = param.typ,
                editor = param.editor,
                defaultValue = param.defaultValue,
                additionalVariables = param.additionalVariables.map { case (key, typingResult) =>
                  (key, typingResult)
                },
                variablesToHide = param.variablesToHide,
                branchParam = param.branchParam,
                hintText = param.hintText,
                label = param.label
              )
            }
          },
          expressionType = node.expressionType.map { typingResult =>
            typingResult
          },
          validationErrors = node.validationErrors,
          validationPerformed = node.validationPerformed
        )
      }

    }

    // to jest chyba tylko response
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
    }

    @derive(schema, encoder, decoder)
    final case class PropertiesValidationRequestDto(
        additionalFields: ProcessAdditionalFields,
        name: ProcessName
    )

    // to jest request
    @derive(schema, decoder)
    final case class ParametersValidationRequestDto(
        parameters: List[UIValueParameterDto],
//        variableTypes: Map[String, TypingResultDto]
        variableTypes: Map[String, Json]
//        variableTypes: Json
    )

    implicit val parametersValidationRequestDtoEncoder: Encoder[ParametersValidationRequestDto] =
      Encoder.encodeJson.contramap[ParametersValidationRequestDto](_ => Json.Null)

    @derive(schema, encoder, decoder)
    final case class ParametersValidationResultDto(
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    // tylko do requestu
    @derive(schema, encoder, decoder)
    final case class UIValueParameterDto(
        name: String,
        typ: Json,
        expression: Expression
    )

    implicit lazy val expressionSchema: Schema[Expression]           = Schema.derived
    implicit lazy val caretPosition2dSchema: Schema[CaretPosition2d] = Schema.derived

    // to jest request
    @derive(schema, decoder)
    final case class ExpressionSuggestionRequestDto(
        expression: Expression,
        caretPosition2d: CaretPosition2d,
//        variableTypes: Map[String, TypingResultDto]
        variableTypes: Map[String, Json]
//        variableTypes: Json
    ) {

      def decodedVariableTypes(decoder: Decoder[TypingResult]): Map[String, TypingResult] =
        variableTypes.map { case (key, result) =>
          (key, decoder.decodeJson(result).getOrElse(Unknown))
        } // todo unknown for now

    }

    implicit val expressionSuggestionRequestDtoEncoder: Encoder[ExpressionSuggestionRequestDto] =
      Encoder.encodeJson.contramap[ExpressionSuggestionRequestDto](_ => Json.Null)

    // to jest response
    @derive(schema, encoder)
    final case class ExpressionSuggestionDto(
        methodName: String,
        refClazz: TypingResult,
        fromClass: Boolean,
        description: Option[String],
        parameters: List[ParameterDto]
    )

    implicit val expressionSuggestionDtoDecoder: Decoder[ExpressionSuggestionDto] =
      Decoder.instance[ExpressionSuggestionDto](_ => ???)

    // to jest response
    @derive(schema, encoder)
    final case class ParameterDto(
        name: String,
        refClazz: TypingResult
    )

    //    Things copied from NodesResource
    //    These 2 are used in ManagmentResources for example

    def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
      new TypingResultDecoder(name =>
        ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
      ).decodeTypingResults
    }

//    These 4 doesn't look as used anywhere

    def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
        deriveConfiguredDecoder[TestSourceParameters]
      deriveConfiguredDecoder[TestFromParametersRequest]
    }

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
              .decodeJson(parameter.typ)
              .getOrElse(Unknown), // todo for now is Unknown, maybe ex should be rethrown
            expression = parameter.expression
          )
        },
        request.variableTypes.map { case (key, typDto) =>
          (key, typingResultDecoder.decodeJson(typDto).getOrElse(Unknown)) // todo the same as higher todo
        }
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
        variableTypes = node.variableTypes.map { case (key, typingResult) =>
          (key, typingResultDecoder.decodeJson(typingResult).getOrElse(Unknown)) // todo else -> unknown for now
        },
        branchVariableTypes = node.branchVariableTypes.map { outerMap =>
          outerMap.map { case (name, innerMap) =>
            val changedMap = innerMap.map { case (key, typing) =>
              (key, typingResultDecoder.decodeJson(typing).getOrElse(Unknown))
            }
            (name, changedMap)
          }
        },
        outgoingEdges = node.outgoingEdges
      )

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
