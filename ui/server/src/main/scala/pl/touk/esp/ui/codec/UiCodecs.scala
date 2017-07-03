package pl.touk.esp.ui.codec

import java.time.LocalDateTime

import argonaut._
import argonaut.derive.{DerivedInstances, JsonSumCodec, JsonSumCodecFor, SingletonInstances}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api
import pl.touk.esp.engine.api.{Displayable, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.esp.engine.api.deployment.test.{ExpressionInvocationResult, MockedResult, TestResults}
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.definition.DefinitionExtractor.PlainClazzDefinition
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.definition.TestingCapabilities
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.util.Codecs
import pl.touk.esp.ui.api.{DisplayableUser, GrafanaSettings, ProcessObjects, ResultsWithCounts}
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{EdgeType, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.esp.ui.process.displayedgraph._
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.esp.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessHistoryEntry}
import pl.touk.esp.ui.processreport.NodeCount
import pl.touk.esp.ui.validation.ValidationResults.ValidationResult

object UiCodecs extends UiCodecs

trait UiCodecs extends Codecs with Argonauts with SingletonInstances with DerivedInstances {

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //w sumie nie rozumiem czemu tak... ale probowalem roznych rzeczy i rozne wyjatki mi rzucalo... lacznie ze stackoverflow...
  implicit def typeCodec : CodecJson[TypeSpecificData] = new ProcessMarshaller().typeSpecificEncoder

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

  implicit def validationResultEncode = EncodeJson.of[ValidationResult]

  //fixme jak to zrobic automatycznie?
  implicit def edgeTypeEncode: EncodeJson[EdgeType] = EncodeJson[EdgeType] {
    case EdgeType.FilterFalse => jObjectFields("type" -> jString("FilterFalse"))
    case EdgeType.FilterTrue => jObjectFields("type" -> jString("FilterTrue"))
    case EdgeType.SwitchDefault => jObjectFields("type" -> jString("SwitchDefault"))
    case ns: EdgeType.NextSwitch => jObjectFields("type" -> jString("NextSwitch"), "condition" -> ns.condition.asJson)
    case EdgeType.SubprocessOutput(name) => jObjectFields("type" -> jString("SubprocessOutput"), "name" -> name.asJson)
  }

  implicit def edgeTypeDecode: DecodeJson[EdgeType] = DecodeJson[EdgeType] { c =>
    for {
      edgeType <- (c --\ "type").as[String]
      edgeTypeObj <- {
        if (edgeType == "FilterFalse") DecodeResult.ok(EdgeType.FilterFalse)
        else if (edgeType == "FilterTrue") DecodeResult.ok(EdgeType.FilterTrue)
        else if (edgeType == "SwitchDefault") DecodeResult.ok(EdgeType.SwitchDefault)
        else if (edgeType == "NextSwitch") (c --\ "condition").as[Expression].map(condition => EdgeType.NextSwitch(condition))
        else if (edgeType == "SubprocessOutput") (c --\ "name").as[String].map(name => EdgeType.SubprocessOutput(name))

        else throw new IllegalArgumentException(s"Unknown edge type: $edgeType")
      }
    } yield edgeTypeObj
  }

  implicit val processTypeCodec = Codecs.enumCodec(ProcessType)

  implicit val processingTypeCodec = Codecs.enumCodec(ProcessingType)

  implicit def edgeEncode = EncodeJson.of[displayablenode.Edge]

  implicit def displayableProcessCodec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def commentCodec = CodecJson.derived[Comment]

  implicit def processActivityCodec = CodecJson.derive[ProcessActivity]

  implicit def processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  implicit def processHistory = EncodeJson.of[ProcessHistoryEntry]

  implicit def processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit def grafanaEncode = EncodeJson.of[GrafanaSettings]

  implicit def userEncodeEncode = EncodeJson.of[DisplayableUser]

  implicit val processDetails : CodecJson[ProcessDetails] = CodecJson.derive[ProcessDetails]

  implicit def printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  //separated from the rest, as we're doing some hacking here...
  //we have hacky codec here, as we want to encode Map[String, Any] of more or less arbitrary objects
  case class ContextCodecs(typesInformation: List[PlainClazzDefinition]) extends LazyLogging {

    val typesWithMethodNames: Map[String, Set[String]]
    = typesInformation.map(ti => ti.clazzName.refClazzName -> ti.methods.keys.toSet).toMap

    implicit def paramsMapEncode = EncodeJson[Map[String, Any]](map => {
      map.filterNot(a => a._2 == None || a._2 == null).mapValues(encodeVariable).asJson
    })

    private def safeJson[T](fun: T => Json) = (value: T) => Option(value) match {
      case Some(realValue) => fun(realValue)
      case None => jNull
    }

    private val safeString = safeJson[String](jString(_))
    private val safeLong = safeJson[Long](jNumber)
    private val safeInt = safeJson[Int](jNumber)
    private val safeDouble = safeJson[Double](jNumber(_))
    private val safeNumber = safeJson[Number](a => jNumber(a.doubleValue()))


    //TODO: cos jeszcze??
    private def encodeVariable(any: Any): Json = {
      if (any == null) {
        jNull
      } else {
        val klass = any.getClass
        any match {
          case Some(a) => encodeVariable(a)
          case s: String => safeString(s)
          case a: Long => safeLong(a)
          case a: Double => safeDouble(a)
          case a: Int => safeInt(a)
          case a: Number => safeNumber(a.doubleValue())
          case a: LocalDateTime => a.asJson
          case a: Displayable => displayableToJson(a)
          //TODO: a to??
          //case a: LocalDate => a.asJson
          //TODO: co tu w sumie lepiej pokazywac??
          //case _ if typesWithMethodNames.contains(klass.getName) =>
          //  printKnownType(any, klass)
          case _ => safeString(any.toString)
        }
      }
    }

    private def displayableToJson(displayable: Displayable): Json = {
      val prettyDisplay = displayable.display.spaces2
      displayable.originalDisplay match {
        case None => jObjectFields("pretty" -> safeString(prettyDisplay))
        case Some(original) => jObjectFields("original" -> safeString(original), "pretty" -> safeString(prettyDisplay))
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
      case api.Context(id, vars, _, _) => jObjectFields(
        "id" -> safeString(id),
        "variables" -> vars.asJson
      )
    }

    implicit def exprInvocationResult = EncodeJson[ExpressionInvocationResult] {
      case ExpressionInvocationResult(context, name, result) => jObjectFields(
        "context" -> context.asJson,
        "name" -> safeString(name),
        "value" -> encodeVariable(result)
      )
    }

    implicit def mockedResult = EncodeJson[MockedResult] {
      case MockedResult(context, name, result) => jObjectFields(
        "context" -> context.asJson,
        "name" -> safeString(name),
        "value" -> encodeVariable(result)
      )
    }

    implicit def exceptionInfo = EncodeJson[EspExceptionInfo[_<:Throwable]] {
      case EspExceptionInfo(nodeId, throwable, ctx) => jObjectFields(
        "nodeId" -> nodeId.asJson,
        "exception" -> jObjectFields(
          "message" -> safeString(throwable.getMessage),
          "class" -> safeString(throwable.getClass.getSimpleName)
        ),
        "context" -> ctx.asJson
      )
    }


    implicit def testResultsEncode = EncodeJson.of[TestResults]

    implicit def resultsWithCountsEncode = EncodeJson.of[ResultsWithCounts]
    
  }

  //unfortunately, this has do be done manually, as argonaut has problems with recursive types...
  implicit val encodeNodeCount : EncodeJson[NodeCount] = EncodeJson[NodeCount] {
    case NodeCount(all, errors, subProcessCounts) => jObjectFields(
      "all" -> jNumber(all),
      "errors" -> jNumber(errors),
      "subprocessCounts" -> jObjectFields(subProcessCounts.mapValues(encodeNodeCount(_)).toList: _*)
    )
  }



}