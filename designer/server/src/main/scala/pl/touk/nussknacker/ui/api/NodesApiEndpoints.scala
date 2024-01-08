package pl.touk.nussknacker.ui.api

import derevo.circe.{decoder, encoder}
import derevo.derive
import io.circe.Json.Null
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.additionalInfo.AdditionalInfo
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.displayedgraph.ProcessProperties
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{
  Typed,
  TypedClass,
  TypedObjectWithValue,
  TypedTaggedValue,
  TypingResult
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.NodeData.nodeDataEncoder
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeValidationError
import pl.touk.nussknacker.security.AuthCredentials
import sttp.model.StatusCode.Ok
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.derevo.schema
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class NodesApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  import NodesApiEndpoints.Dtos._

  lazy val nodesAdditionalInfoEndpoint: SecuredEndpoint[(ProcessName, NodeData), Unit, Option[AdditionalInfo], Any] = {
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
      .withSecurity(auth)
  }

  lazy val nodesValidationEndpoint
      : SecuredEndpoint[(ProcessName, NodeValidationRequestDto), Unit, NodeValidationResultDto, Any] = {
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
      .withSecurity(auth)
  }

  lazy val propertiesAdditionalInfoEndpoint
      : SecuredEndpoint[(ProcessName, ProcessProperties), Unit, Option[AdditionalInfo], Any] = {
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
      .withSecurity(auth)
  }

  lazy val propertiesValidationEndpoint
      : SecuredEndpoint[(ProcessName, PropertiesValidationRequestDto), Unit, NodeValidationResultDto, Any] = {
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
        params: List[String]
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
                "params"       -> typingResult.params.asJson
              )
            case value =>
              Json.obj(
                "value"        -> value,
                "display"      -> Json.fromString(typingResult.display),
                "type"         -> Json.fromString(typingResult.`type`),
                "refClazzName" -> Json.fromString(typingResult.refClazzName),
                "params"       -> typingResult.params.asJson
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
            params       <- c.downField("params").as[List[String]]
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
              params = result.params.map(sth => sth.toString)
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
          case _result: typing.KnownTypingResult =>
            println("KnownTypingResult")
            TypingResultDto(None, "rest", "type", "refClazzName", List.empty)
        }
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

  }

}
