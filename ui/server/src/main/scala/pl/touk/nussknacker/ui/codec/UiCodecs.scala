package pl.touk.nussknacker.ui.codec

import argonaut._
import argonaut.derive.{DerivedInstances, JsonSumCodec, JsonSumCodecFor, SingletonInstances}
import pl.touk.nussknacker.engine.api.{Displayable, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExceptionResult, TestResults}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.definition.TestingCapabilities
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.api.{DisplayableUser, MetricsSettings, ProcessObjects}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessType
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.{EdgeType, NodeAdditionalFields, ProcessAdditionalFields}
import pl.touk.nussknacker.ui.process.displayedgraph._
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessHistoryEntry}
import pl.touk.nussknacker.ui.processreport.NodeCount
import pl.touk.nussknacker.ui.validation.ValidationResults.{NodeValidationErrorType, ValidationResult}
import ArgonautShapeless._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.QueryServiceResult
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, typing}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.json.{BestEffortJsonEncoder, Codecs}
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.QueryResult

object UiCodecs extends UiCodecs

trait UiCodecs extends Codecs with Argonauts with SingletonInstances with DerivedInstances {

  import pl.touk.nussknacker.engine.api.typed.TypeEncoders._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
    JsonSumCodecFor(JsonSumCodec.typeField)

  //not sure why it works, another argonaut issue...
  implicit def typeCodec : CodecJson[TypeSpecificData] = new ProcessMarshaller().typeSpecificEncoder

  implicit val nodeDataCodec = CodecJson.derived[NodeData]

  //argonaut does not like covariation so wee need to cast
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

  implicit def nodeErrorsCodec = Codecs.enumCodec(NodeValidationErrorType)

  implicit def validationResultEncode = CodecJson.derive[ValidationResult]

  //fixme how to do this automatically?
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
  
  implicit def edgeEncode = EncodeJson.of[displayablenode.Edge]

  implicit def displayableProcessCodec: CodecJson[DisplayableProcess] = CodecJson.derive[DisplayableProcess]

  implicit def validatedDisplayableProcessCodec: CodecJson[ValidatedDisplayableProcess] = CodecJson.derive[ValidatedDisplayableProcess]

  implicit def commentCodec = CodecJson.derived[Comment]

  implicit def processActivityCodec = CodecJson.derive[ProcessActivity]



  //FIXME: what should we do here? not always we have classs!
  implicit val typingResultDummyDecode : DecodeJson[TypingResult] = DecodeJson(_ => DecodeResult.ok(typing.Unknown))

  implicit def processObjectsEncodeEncode = EncodeJson.of[ProcessObjects]

  implicit def objectDefinitionEncode = EncodeJson.of[ObjectDefinition]

  implicit def processHistory = EncodeJson.of[ProcessHistoryEntry]

  implicit def processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit def grafanaEncode = EncodeJson.of[MetricsSettings]

  implicit def userEncodeEncode = EncodeJson.of[DisplayableUser]

  //unfortunately, this has do be done manually, as argonaut has problems with recursive types...
  implicit val encodeNodeCount : EncodeJson[NodeCount] = EncodeJson[NodeCount] {
    case NodeCount(all, errors, subProcessCounts) => jObjectFields(
      "all" -> jNumber(all),
      "errors" -> jNumber(errors),
      "subprocessCounts" -> jObjectFields(subProcessCounts.mapValues(encodeNodeCount(_)).toList: _*)
    )
  }

  private def safeString(a: String) = Option(a).map(jString).getOrElse(jNull)

  private implicit val exceptionInfo: EncodeJson[ExceptionResult[Json]] = EncodeJson[ExceptionResult[Json]] {
    case ExceptionResult(ctx, nodeId, throwable) => jObjectFields(
      "nodeId" -> nodeId.asJson,
      "exception" -> jObjectFields(
        "message" -> safeString(throwable.getMessage),
        "class" -> safeString(throwable.getClass.getSimpleName)
      ),
      "context" -> ctx.asJson
    )
  }

  implicit val testResultsEncoder: EncodeJson[TestResults[Json]] = EncodeJson[TestResults[Json]] {
    case TestResults(nodeResults, invocationResults, mockedResults, exceptions, _) => jObjectFields(
      "nodeResults" -> nodeResults.asJson,
      "invocationResults" -> invocationResults.asJson,
      "mockedResults" -> mockedResults.asJson,
      "exceptions" -> exceptions.asJson
    )
  }

  val testResultsVariableEncoder : Any => Json = {
    BestEffortJsonEncoder(failOnUnkown = false, {
      case displayable: Displayable =>
        val prettyDisplay = displayable.display.spaces2
        def safeString(a: String) = Option(a).map(jString).getOrElse(jNull)

        displayable.originalDisplay match {
          case None => jObjectFields("pretty" -> safeString(prettyDisplay))
          case Some(original) => jObjectFields("original" -> safeString(original), "pretty" -> safeString(prettyDisplay))
        }
    }).encode _
  }

  implicit def queryServiceResult = {
    implicit val encoder: EncodeJson[Any] = EncodeJson.apply { a =>
      BestEffortJsonEncoder(failOnUnkown = false).encode(a)
    }
    EncodeJson.derive[QueryServiceResult]
  }

  implicit def queryResult = {
    implicit val encoder: EncodeJson[Any] = EncodeJson.apply { a =>
      BestEffortJsonEncoder(failOnUnkown = false).encode(a)
    }
    EncodeJson.derive[QueryResult]
  }


}