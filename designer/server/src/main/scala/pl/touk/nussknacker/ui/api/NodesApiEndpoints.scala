package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.Json.Null
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.graph.ProcessProperties
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectWithValue, TypedTaggedValue, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.engine.spel.Parameter
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.UIValueParameter
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDto.toTypingResult
import pl.touk.nussknacker.ui.suggester.CaretPosition2d
import sttp.model.StatusCode.{NotFound, Ok}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._

  lazy val nodesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, NodeData), String, Option[AdditionalInfo], Any] = {
    baseNuApiEndpoint
      .summary("Additional info for provided node")
      .tag("Nodes")
      .post
      .in("nodess" / path[ProcessName]("processName") / "additionalInfo")
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
      .in("nodess" / path[ProcessName]("processName") / "validation")
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
      .in("propertiess" / path[ProcessName]("processName") / "additionalInfo")
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
      .in("propertiess" / path[ProcessName]("processName") / "validation")
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

  //  Almost -> error messages are almost correct, they are less specific for now
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
      .in("parameterss" / path[ProcessingType]("processingType") / "validate")
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
      .in("parameterss" / path[ProcessingType]("processingType") / "suggestions")
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

    def encode(processName: ProcessName): String = processName.value

    def decode(s: String): DecodeResult[ProcessName] = {
      val processName = ProcessName.apply(s)
      DecodeResult.Value(processName)
    }

    implicit val processNameCodec: PlainCodec[ProcessName] = Codec.string.mapDecode(decode)(encode)
    implicit val processNameSchema: Schema[ProcessName]    = Schema.derived

    implicit val additionalInfoSchema: Schema[AdditionalInfo] = Schema.derived

    implicit val processAdditionalFieldsSchema: Schema[ProcessAdditionalFields] = Schema.derived

    final case class TypingResultDto(
        value: Option[Any],
        display: String,
        `type`: String,
        refClazzName: String,
        params: List[TypingResultDto]
    )

    object TypingResultDto {

      implicit val typingSchema: Schema[TypingResultDto] = {
        Schema.any
      }

      def getTypingValue(value: Any): Json = {
        value match {
          case Some(inside) =>
            inside match {
              case x: String  => Json.fromString(x)
              case x: Int     => Json.fromInt(x)
              case x: Boolean => Json.fromBoolean(x)
              case _          => Json.Null
            }
          case None => Json.Null
        }
      }

      implicit val encoder: Encoder[TypingResultDto] = {
        Encoder.encodeJson.contramap { typingResult =>
          getTypingValue(typingResult.value) match {
            case Null =>
              Json.obj(
                "display"      -> Json.fromString(typingResult.display),
                "type"         -> Json.fromString(typingResult.`type`),
                "refClazzName" -> Json.fromString(typingResult.refClazzName),
                "params"       -> Json.fromValues(typingResult.params.map { result => result.asJson(encoder) })
              )
            case value =>
              Json.obj(
                "value"        -> value,
                "display"      -> Json.fromString(typingResult.display),
                "type"         -> Json.fromString(typingResult.`type`),
                "refClazzName" -> Json.fromString(typingResult.refClazzName),
                "params"       -> Json.fromValues(typingResult.params.map { result => result.asJson(encoder) })
              )
          }
        }
      }

      implicit val decoder: Decoder[TypingResultDto] = {
        Decoder.instance { c =>
          for {
            typ <- c.downField("type").as[String]

            //            variableType  <- giveType(typ, c)
            //            value         <- c.downField("value").
            display      <- c.downField("display").as[String]
            refClazzName <- c.downField("refClazzName").as[String]
            params <- c.downField("params").as[List[TypingResultDto]](Decoder.decodeList[TypingResultDto](decoder))
          } yield TypingResultDto(None, display, typ, refClazzName, params)
        }
      }

      def typingResultToDto(typingResult: TypingResult): TypingResultDto = {
        typingResult match {
          case result: TypedObjectWithValue =>
            println("TypedObjectWithValue")
            TypingResultDto(
              value = Some(result.value),
              display = result.display,
              `type` = typing.TypedObjectWithValue.toString(),
              refClazzName = result.data.getClass.toString.stripPrefix("class "),
              params = List.empty
            )
          case result: TypedClass =>
            println("TypedClass")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = "TypedClass",
              refClazzName = result.klass.toString.stripPrefix("class "),
              params = result.params.map(param => typingResultToDto(param))
            )
          case result: TypedTaggedValue =>
            println("TypedTaggedValue")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = typing.TypedTaggedValue.toString(),
              refClazzName = result.data.getClass.toString.stripPrefix("class "),
              params = List.empty
            )
          case result: typing.TypedObjectTypingResult =>
            println("TypedObjectTypingResult")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = typing.TypedObjectTypingResult.getClass.toString,
              refClazzName = result.getClass.toString.stripPrefix("class "),
              params = List.empty // maybe sth else?
            )
          case result: typing.TypedObjectWithData =>
            println("TypedObjectWithData")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = typing.TypedObjectWithValue.toString(), // wrong
              refClazzName = result.withoutValue.display.getClass.toString,
              params = List.empty
            )
          case result: typing.SingleTypingResult =>
            println("SingleTypingResult")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = typing.TypedObjectWithValue.toString(),
              refClazzName = result.getClass.toString.stripPrefix("class "),
              params = List.empty
            )
          case typing.TypedNull =>
            println("TypedNull")
            TypingResultDto(None, "null", "null", "null", List.empty)
          case typing.Unknown =>
            println("Unknown")
            TypingResultDto(None, "Unknown", "Unknown", "java.lang.Object", List.empty)
          case _: typing.KnownTypingResult =>
            println("KnownTypingResult")
            TypingResultDto(None, "rest", "type", "refClazzName", List.empty)
        }
      }

      def toTypingResult(typeDto: TypingResultDto): TypingResult = {
        Typed.genericTypeClass(
          Class.forName(typeDto.refClazzName),
          typeDto.params.map { result => toTypingResult(result) }
        )
      }

    }

    @derive(encoder, decoder, schema)
    final case class NodeValidationRequestDto(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResultDto],                            // String -> TypingResult
        branchVariableTypes: Option[Map[String, Map[String, TypingResultDto]]], // last map String -> TypingResult
        outgoingEdges: Option[List[Edge]]
    ) {

      def toRequest: Option[NodeValidationRequest] = {
        try {
          Some(
            NodeValidationRequest(
              nodeData = nodeData,
              processProperties = processProperties,
              variableTypes = variableTypes.map { case (key, typingResultDto) =>
                (key, Typed.apply(Class.forName(typingResultDto.refClazzName)))
              },
              branchVariableTypes = branchVariableTypes.map { outerMap =>
                outerMap.map { case (name, innerMap) =>
                  val changedMap = innerMap.map { case (key, typingResultDto) =>
                    (key, Typed.apply(Class.forName(typingResultDto.refClazzName)))
                  }
                  (name, changedMap)
                }
              },
              outgoingEdges = outgoingEdges
            )
          )
        } catch {
          case _: Throwable => None
        }

      }

    }

    object NodeValidationRequestDto {
      implicit val nodeDataSchema: Schema[NodeData]                   = Schema.anyObject
      implicit val processPropertiesSchema: Schema[ProcessProperties] = Schema.any
    }

    @derive(encoder, decoder, schema)
    final case class NodeValidationResultDto(
        parameters: Option[List[UIParameterDto]],
        expressionType: Option[TypingResultDto],
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    @derive(encoder, decoder, schema)
    final case class UIParameterDto(
        name: String,
        typ: TypingResultDto,
        editor: ParameterEditor,
        defaultValue: Expression,
        additionalVariables: Map[String, TypingResultDto],
        variablesToHide: Set[String],
        branchParam: Boolean,
        hintText: Option[String]
    )

    object UIParameterDto {
      implicit val parameterEditorSchema: Schema[ParameterEditor]    = Schema.anyObject
      implicit val dualEditorSchema: Schema[DualEditorMode]          = Schema.string
      implicit val expressionSchema: Schema[Expression]              = Schema.derived
      implicit val timeSchema: Schema[java.time.temporal.ChronoUnit] = Schema.anyObject
    }

    @derive(schema, encoder, decoder)
    final case class PropertiesValidationRequestDto(
        additionalFields: ProcessAdditionalFields,
        name: ProcessName
    )

    @derive(schema, encoder, decoder)
    final case class ParametersValidationRequestDto(
        parameters: List[UIValueParameterDto],
        variableTypes: Map[String, TypingResultDto]
    ) {

      def withoutDto: ParametersValidationRequest = {
        //        println("halo from withoutDto")
        ParametersValidationRequest(
          parameters.map { parameter =>
            UIValueParameter(
              name = parameter.name,
              typ = toTypingResult(parameter.typ),
              expression = parameter.expression
            )
          },
          variableTypes.map { case (key, typDto) => (key, toTypingResult(typDto)) }
        )
      }

    }

    @derive(schema, encoder, decoder)
    final case class ParametersValidationResultDto(
        validationErrors: List[NodeValidationError],
        validationPerformed: Boolean
    )

    @derive(schema, encoder, decoder)
    final case class UIValueParameterDto(
        name: String,
        typ: TypingResultDto,
        expression: Expression
    )

    implicit val expressionSchema: Schema[Expression]           = Schema.derived
    implicit val caretPosition2dSchema: Schema[CaretPosition2d] = Schema.derived

    @derive(schema, encoder, decoder)
    final case class ExpressionSuggestionRequestDto(
        expression: Expression,
        caretPosition2d: CaretPosition2d,
        variableTypes: Map[String, TypingResultDto]
    )

    object ExpressionSuggestionRequestDto {
      //      implicit val caretPosition2dEncoder: Encoder[CaretPosition2d] = deriveConfiguredEncoder[CaretPosition2d]
    }

    @derive(schema, encoder, decoder)
    final case class ExpressionSuggestionDto(
        methodName: String,
        refClazz: TypingResultDto,
        fromClass: Boolean,
        description: Option[String],
        parameters: List[ParameterDto]
    )

    @derive(schema, encoder, decoder)
    final case class ParameterDto(
        name: String,
        refClazz: TypingResultDto
    )

  }

}
