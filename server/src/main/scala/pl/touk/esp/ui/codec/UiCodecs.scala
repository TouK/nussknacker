package pl.touk.esp.ui.codec

import java.time.{LocalDate, LocalDateTime}

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoader
import pl.touk.esp.engine.api
import pl.touk.esp.engine.api.{Displayable, UserDefinedProcessAdditionalFields}
import pl.touk.esp.engine.api.deployment.test.{ExpressionInvocationResult, MockedResult, TestResults}
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.definition.DefinitionExtractor.PlainClazzDefinition
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.definition.TestingCapabilities
import pl.touk.esp.engine.graph.node
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.{DisplayableUser, GrafanaSettings, ProcessObjects}
import pl.touk.esp.ui.app.BuildInfo
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{EdgeType, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties, displayablenode}
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.esp.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessHistoryEntry}

object UiCodecs {

  import ArgonautShapeless._
  import Argonaut._


  //rzutujemy bo argonaut nie lubi kowariancji...
  implicit def nodeAdditionalFieldsOptCodec: CodecJson[Option[node.UserDefinedAdditionalNodeFields]] = {
    CodecJson.derived[Option[NodeAdditionalFields]]
      .asInstanceOf[CodecJson[Option[node.UserDefinedAdditionalNodeFields]]]
  }

  implicit def processAdditionalFieldsOptCodec: CodecJson[Option[UserDefinedProcessAdditionalFields]] = {
    CodecJson.derived[Option[ProcessAdditionalFields]]
      .asInstanceOf[CodecJson[Option[UserDefinedProcessAdditionalFields]]]
  }

  implicit def testingCapabilitiesCodec: CodecJson[TestingCapabilities] = CodecJson.derive[TestingCapabilities]

  implicit def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

  implicit def localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)

  implicit def localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))

  implicit def validationResultEncode = EncodeJson.of[ValidationResult]

  //fixme jak to zrobic automatycznie?
  implicit def edgeTypeEncode: EncodeJson[EdgeType] = EncodeJson[EdgeType] {
    case EdgeType.FilterFalse => jObjectFields("type" -> jString("FilterFalse"))
    case EdgeType.FilterTrue => jObjectFields("type" -> jString("FilterTrue"))
    case EdgeType.SwitchDefault => jObjectFields("type" -> jString("SwitchDefault"))
    case ns: EdgeType.NextSwitch => jObjectFields("type" -> jString("NextSwitch"), "condition" -> ns.condition.asJson)
  }

  implicit def edgeTypeDecode: DecodeJson[EdgeType] = DecodeJson[EdgeType] { c =>
    for {
      edgeType <- (c --\ "type").as[String]
      edgeTypeObj <- {
        if (edgeType == "FilterFalse") DecodeResult.ok(EdgeType.FilterFalse)
        else if (edgeType == "FilterTrue") DecodeResult.ok(EdgeType.FilterTrue)
        else if (edgeType == "SwitchDefault") DecodeResult.ok(EdgeType.SwitchDefault)
        else if (edgeType == "NextSwitch") (c --\ "condition").as[Expression].map(condition => EdgeType.NextSwitch(condition))
        else throw new IllegalArgumentException(s"Unknown edge type: $edgeType")
      }
    } yield edgeTypeObj
  }

  implicit def edgeEncode = EncodeJson.of[displayablenode.Edge]

  implicit def displayableProcessCodec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def commentCodec = CodecJson.derived[Comment]

  implicit def processActivityCodec = CodecJson.derive[ProcessActivity]

  implicit def processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  implicit def processTypeCodec = ProcessTypeCodec.codec

  implicit def processingTypeCodec = ProcessingTypeCodec.codec

  implicit def processHistory = EncodeJson.of[ProcessHistoryEntry]

  implicit def processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit def grafanaEncode = EncodeJson.of[GrafanaSettings]

  implicit def userEncodeEncode = EncodeJson.of[DisplayableUser]

  implicit def printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //separated from the rest, as we're doing some hacking here...
  //we have hacky codec here, as we want to encode Map[String, Any] of more or less arbitrary objects
  case class ContextCodecs(typesInformation: List[PlainClazzDefinition]) extends LazyLogging {

    val typesWithMethodNames: Map[String, Set[String]]
    = typesInformation.map(ti => ti.clazzName.refClazzName -> ti.methods.keys.toSet).toMap

    implicit def paramsMapEncode = EncodeJson[Map[String, Any]](map => {
      map.filterNot(a => a._2 == None || a._2 == null).mapValues(encodeVariable).asJson
    })

    //TODO: cos jeszcze??
    private def encodeVariable(any: Any): Json = {
      if (any == null) {
        jNull
      } else {
        val klass = any.getClass
        any match {
          case Some(a) => encodeVariable(a)
          case s: String => jString(s)
          case a: Long => jNumber(a)
          case a: Double => jNumber(a)
          case a: Int => jNumber(a)
          case a: Number => jNumber(a.doubleValue())
          case a: LocalDateTime => a.asJson
          case a: Displayable => displayableToJson(a)
          //TODO: a to??
          //case a: LocalDate => a.asJson
          //TODO: co tu w sumie lepiej pokazywac??
          //case _ if typesWithMethodNames.contains(klass.getName) =>
          //  printKnownType(any, klass)
          case _ => jString(any.toString)
        }
      }
    }

    private def displayableToJson(displayable: Displayable): Json = {
      val prettyDisplay = displayable.display.spaces2
      displayable.originalDisplay match {
        case None => jObjectFields("pretty" -> jString(prettyDisplay))
        case Some(original) => jObjectFields("original" -> jString(original), "pretty" -> jString(prettyDisplay))
      }
    }

    def printKnownType(any: Any, klass: Class[_]): Json = {
      val methods = typesWithMethodNames(klass.getName)
      klass.getDeclaredFields
        .filter(f => methods.contains(f.getName))
        .map { field =>
          field.setAccessible(true)
          field.getName -> field.get(any).asInstanceOf[Any]
        }.toMap.asJson
    }

    implicit def ctxEncode = EncodeJson[api.Context] {
      case api.Context(id, vars, _) => jObjectFields(
        "id" -> jString(id),
        "variables" -> vars.asJson
      )
    }

    implicit def exprInvocationResult = EncodeJson[ExpressionInvocationResult] {
      case ExpressionInvocationResult(context, name, result) => jObjectFields(
        "context" -> context.asJson,
        "name" -> jString(name),
        "value" -> encodeVariable(result)
      )
    }

    implicit def mockedResult = EncodeJson[MockedResult] {
      case MockedResult(context, name, result) => jObjectFields(
        "context" -> context.asJson,
        "name" -> jString(name),
        "value" -> encodeVariable(result)
      )
    }

    implicit def exceptionInfo = EncodeJson[EspExceptionInfo[_<:Throwable]] {
      case EspExceptionInfo(nodeId, throwable, ctx) => jObjectFields(
        "nodeId" -> nodeId.asJson,
        "exception" -> jObjectFields(
          "message" -> jString(throwable.getMessage),
          "class" -> jString(throwable.getClass.getSimpleName)
        ),
        "context" -> ctx.asJson
      )
    }

    implicit def testResultsEncode = EncodeJson.of[TestResults]
  }

}