package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.engine.api.CirceUtil._
import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.generic.JsonCodec
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json, JsonNumber, KeyDecoder}
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
import pl.touk.nussknacker.engine.api.typed.{TypingResultDecoder, typing}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectWithValue, TypedTaggedValue, TypedUnion, TypingResult}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.definition.{UIParameter, UIValueParameter}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.TypingResultDto.{toTypingResult, typingResultToDto}
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{NodeValidationResultDto, UIParameterDto}
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
      .in("nodes" / path[ProcessName]("processName") / "additionalInfo")
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
      .in("nodes" / path[ProcessName]("processName") / "validation")
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
      .in("properties" / path[ProcessName]("processName") / "additionalInfo")
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
      .in("properties" / path[ProcessName]("processName") / "validation")
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
        params: List[TypingResultDto],
        fields: Option[Map[String, TypingResultDto]]
    )

    object TypingResultDto {

      implicit val typingSchema: Schema[TypingResultDto] = {
        Schema.any
      }

      private def getTypingValue(value: Any): Json = {
        value match {
          case x: Long       => Json.fromLong(x)
          case x: String     => Json.fromString(x)
          case x: Int        => Json.fromInt(x)
          case x: Boolean    => Json.fromBoolean(x)
          case x: JsonNumber => x.asJson
          case x: Json       => x
          case _             => Json.Null
        }
      }

      implicit val encoder: Encoder[TypingResultDto] = {
        Encoder.encodeJson.contramap { typingResult =>
          typingResult.value match {
            case None =>
              typingResult.fields match {
                case Some(fields) =>
                  Json.obj(
                    "display" -> Json.fromString(typingResult.display),
                    "type"    -> Json.fromString(typingResult.`type`),
                    "fields" -> Json.fromFields(fields.toList.map { case (key, typingResultDto) =>
                      (key, typingResultDto.asJson(encoder))
                    }),
                    "refClazzName" -> Json.fromString(typingResult.refClazzName),
                    "params"       -> Json.fromValues(typingResult.params.map { result => result.asJson(encoder) })
                  )
                case None =>
                  Json.obj(
                    "display"      -> Json.fromString(typingResult.display),
                    "type"         -> Json.fromString(typingResult.`type`),
                    "refClazzName" -> Json.fromString(typingResult.refClazzName),
                    "params"       -> Json.fromValues(typingResult.params.map { result => result.asJson(encoder) })
                  )
              }
            case Some(value) =>
              Json.obj(
                "value"        -> getTypingValue(value),
                "display"      -> Json.fromString(typingResult.display),
                "type"         -> Json.fromString(typingResult.`type`),
                "refClazzName" -> Json.fromString(typingResult.refClazzName),
                "params"       -> Json.fromValues(typingResult.params.map { result => result.asJson(encoder) })
              )
          }
        }
      }

      implicit val decoder: Decoder[TypingResultDto] = {
        Decoder.instance { c: HCursor =>
          for {
            typ          <- c.downField("type").as[String]
            value        <- c.downField("value").as[Option[Json]]
            display      <- c.downField("display").as[String]
            refClazzName <- c.downField("refClazzName").as[String]
            fields <- c
              .downField("fields")
              .as[Option[Map[String, TypingResultDto]]](
                Decoder.decodeOption(Decoder.decodeMap[String, TypingResultDto](KeyDecoder.decodeKeyString, decoder))
              )
            params <- c.downField("params").as[List[TypingResultDto]](Decoder.decodeList[TypingResultDto](decoder))
          } yield TypingResultDto(value, display, typ, refClazzName, params, fields)
        }
      }

      private def jsonToValue(value: Any, klass: String): Any = {
        klass match {
          case "java.lang.Long" => value.asInstanceOf[Json].asNumber.get.toLong.get
          case "java.lang.Int"  => value.asInstanceOf[Json].asNumber.get.toInt.get
          case _                => value
        }
      }

      def toTypingResult(typeDto: TypingResultDto)(implicit modelData: ModelData): TypingResult = {
        typeDto.value match {
          case Some(value) =>
            val underlying = Typed.genericTypeClass(
              modelData.modelClassLoader.classLoader.loadClass(typeDto.refClazzName),
              typeDto.params.map { resultDto => toTypingResult(resultDto) }
            )

            val trueValue = jsonToValue(value, typeDto.refClazzName)
            typing.TypedObjectWithValue(underlying, trueValue)

          case None =>
            typeDto.fields match {
              case Some(fields) =>
                typing.TypedObjectTypingResult(
                  fields.map { case (key, result) => (key, toTypingResult(result)) }
                )
              case None =>
                Typed.genericTypeClass(
                  modelData.modelClassLoader.classLoader.loadClass(typeDto.refClazzName),
                  typeDto.params.map { resultDto => toTypingResult(resultDto) }
                )
            }
        }
      }

      private def toOptionString(valueOpt: Option[Any]): Option[String] =
        valueOpt match {
          case Some(value) => Some(value.toString)
          case None        => None
        }

      def typingResultToDto(typingResult: TypingResult): TypingResultDto = {
        typingResult match {
          case result: TypedObjectWithValue =>
            println("TypedObjectWithValue")
            TypingResultDto(
              value = Some(result.value),
              display = result.display,
              `type` = typing.TypedObjectWithValue.toString(),
              refClazzName = result.underlying.klass.toString.stripPrefix("class "),
              params = List.empty,
              fields = None
            )
          case result: TypedClass =>
            println("TypedClass")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = "TypedClass",
              refClazzName = result.klass.toString.stripPrefix("class "),
              params = result.params.map(param => typingResultToDto(param)),
              fields = None
            )
          case result: TypedUnion =>
            println("TypedUnion")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = TypedUnion.getClass.toString.stripPrefix("class "),
              refClazzName = "TypedUnion",
              params = List.empty,
              fields = None
            )
          case result: TypedTaggedValue =>
            println("TypedTaggedValue")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = "TypedTaggedValue",
              refClazzName = result.data.getClass.toString.stripPrefix("class "),
              params = List.empty,
              fields = None
            )
          case result: typing.TypedObjectWithData =>
            println("TypedObjectWithData")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = "TypedObjectWithData",
              refClazzName = result.withoutValue.display.getClass.toString,
              params = List.empty,
              fields = None
            )
          case result: typing.TypedDict =>
            println("TypedDict")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = typing.TypedDict.getClass.toString.stripPrefix("class "),
              refClazzName = result.objType.klass.toString,
              params = List.empty,
              fields = None
            )
          case result: typing.TypedObjectTypingResult =>
            println("TypedObjectTypingResult")
            TypingResultDto(
              value = None,
              display = result.display,
              `type` = "TypedObjectTypingResult",
              refClazzName = "java.util.Map",
              params = List(typingResultToDto(Typed.genericTypeClass(Class.forName("java.lang.String"), List.empty))) ++
                result.fields.values.toList.map { res => typingResultToDto(res) },
              fields = Some(result.fields.map { case (key, result) => (key, typingResultToDto(result)) })
            )
          case result: typing.SingleTypingResult =>
            println("SingleTypingResult")
            TypingResultDto(
              value = toOptionString(result.valueOpt),
              display = result.display,
              `type` = typing.TypedObjectWithValue.toString(),
              refClazzName = result.getClass.toString.stripPrefix("class "),
              params = List.empty,
              fields = None
            )
          case typing.TypedNull =>
            println("TypedNull")
            TypingResultDto(Some(null), "Null", "null", "null", List.empty, None)
          case typing.Unknown =>
            println("Unknown")
            TypingResultDto(None, "Unknown", "Unknown", "java.lang.Object", List.empty, None)
          case _ =>
            println("KnownTypingResult?")
            TypingResultDto(None, "rest", "type", "refClazzName", List.empty, None)
        }
      }

    }

    @derive(encoder, decoder, schema)
    final case class NodeValidationRequestDto(
        nodeData: NodeData,
        processProperties: ProcessProperties,
        variableTypes: Map[String, TypingResultDto],
        branchVariableTypes: Option[Map[String, Map[String, TypingResultDto]]],
        outgoingEdges: Option[List[Edge]]
    ) {

      def toRequest(implicit modelData: ModelData): Option[NodeValidationRequest] = {
        try {
          Some(
            NodeValidationRequest(
              nodeData = nodeData,
              processProperties = processProperties,
              variableTypes = variableTypes.map { case (key, typingResultDto) =>
                (key, toTypingResult(typingResultDto))
              },
              branchVariableTypes = branchVariableTypes.map { outerMap =>
                outerMap.map { case (name, innerMap) =>
                  val changedMap = innerMap.map { case (key, typing) =>
                    (key, toTypingResult(typing))
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

      def withoutDto()(implicit modelData: ModelData): ParametersValidationRequest = {
        ParametersValidationRequest(
          parameters.map { parameter =>
            UIValueParameter(
              name = parameter.name,
              typ = toTypingResult(parameter.typ),
              expression = parameter.expression
            )
          },
          variableTypes.map { case (key, typDto) =>
            (key, toTypingResult(typDto))
          }
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

    def prepareTypingResultDecoder(modelData: ModelData): Decoder[TypingResult] = {
      new TypingResultDecoder(name =>
        ClassUtils.forName(name, modelData.modelClassLoader.classLoader)
      ).decodeTypingResults
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

    def prepareTestFromParametersDecoder(modelData: ModelData): Decoder[TestFromParametersRequest] = {
      implicit val typeDecoder: Decoder[TypingResult] = prepareTypingResultDecoder(modelData)
      implicit val testSourceParametersDecoder: Decoder[TestSourceParameters] =
        deriveConfiguredDecoder[TestSourceParameters]
      deriveConfiguredDecoder[TestFromParametersRequest]
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
      displayableProcess: ScenarioGraph
  )

  @JsonCodec(encodeOnly = true) final case class ParametersValidationResult(
      validationErrors: List[NodeValidationError],
      validationPerformed: Boolean
  )

  @JsonCodec(encodeOnly = true) final case class ParametersValidationRequest(
      parameters: List[UIValueParameter],
      variableTypes: Map[String, TypingResult]
  )

  @JsonCodec(encodeOnly = true) final case class NodeValidationResult(
      parameters: Option[List[UIParameter]],
      expressionType: Option[TypingResult],
      validationErrors: List[NodeValidationError],
      validationPerformed: Boolean
  ) {

    def toDto(): Dtos.NodeValidationResultDto = {
      NodeValidationResultDto(
        parameters = parameters.map { list =>
          list.map { param =>
            UIParameterDto(
              name = param.name,
              typ = typingResultToDto(param.typ),
              editor = param.editor,
              defaultValue = param.defaultValue,
              additionalVariables = param.additionalVariables.map { case (key, typingResult) =>
                (key, typingResultToDto(typingResult))
              },
              variablesToHide = param.variablesToHide,
              branchParam = param.branchParam,
              hintText = param.hintText
            )
          }
        },
        expressionType = expressionType.map { typingResult =>
          typingResultToDto(typingResult)
        },
        validationErrors = validationErrors,
        validationPerformed = validationPerformed
      )
    }

  }

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
