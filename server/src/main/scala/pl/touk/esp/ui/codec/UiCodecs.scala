package pl.touk.esp.ui.codec

import java.time.{LocalDate, LocalDateTime}

import argonaut._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.esp.engine.api
import pl.touk.esp.engine.api.UserDefinedProcessAdditionalFields
import pl.touk.esp.engine.api.deployment.test.{ExpressionInvocationResult, TestResults}
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.definition.DefinitionExtractor.PlainClazzDefinition
import pl.touk.esp.engine.graph.node
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.{DisplayableUser, GrafanaSettings, ProcessObjects}
import pl.touk.esp.ui.app.BuildInfo
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
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

  implicit def propertiesCodec: CodecJson[ProcessProperties] = CodecJson.derive[ProcessProperties]

  implicit def localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)

  implicit def localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))

  implicit def validationResultEncode = EncodeJson.of[ValidationResult]

  implicit def codec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def commentCodec = CodecJson.derived[Comment]

  implicit def processActivityCodec = CodecJson.derive[ProcessActivity]

  implicit def processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  implicit def processTypeCodec = ProcessTypeCodec.codec

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
  case class ContextCodecs(typesInformation: List[PlainClazzDefinition]) {

    val typesWithMethodNames: Map[String, Set[String]]
    = typesInformation.map(ti => ti.clazzName.refClazzName -> ti.methods.keys.toSet).toMap

    implicit def paramsMapEncode = EncodeJson[Map[String, Any]](map => {
      map.filterNot(a => a._2 == None || a._2 == null).mapValues(encodeVariable).asJson
    })

    //TODO: cos jeszcze??
    private def encodeVariable(any: Any): Json = {
      val klass = any.getClass
      any match {
        case Some(a) => encodeVariable(a)
        case s: String => jString(s)
        case a: Long => jNumber(a)
        case a: Double => jNumber(a)
        case a: Int => jNumber(a)
        case a: Number => jNumber(a.doubleValue())
        case a: LocalDateTime => a.asJson
        //TODO: a to??
        //case a: LocalDate => a.asJson
        //TODO: co tu w sumie lepiej pokazywac??
        //case _ if typesWithMethodNames.contains(klass.getName) =>
        //  printKnownType(any, klass)
        case _ => jString(any.toString)
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

    implicit def exprInvocationResult =   EncodeJson[ExpressionInvocationResult] {
      case ExpressionInvocationResult(context, name, result) => jObjectFields(
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