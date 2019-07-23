package pl.touk.nussknacker.ui.codec

import argonaut._
import pl.touk.nussknacker.engine.api.Displayable
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExceptionResult, TestResults}
import pl.touk.nussknacker.ui.api.{DisplayableUser, MetricsSettings}
import pl.touk.nussknacker.restmodel.RestModelCodecs
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.{Comment, ProcessActivity}
import pl.touk.nussknacker.ui.processreport.NodeCount
import ArgonautShapeless._
import argonaut.derive.{JsonSumCodec, JsonSumCodecFor}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.QueryServiceResult
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.QueryResult
import pl.touk.nussknacker.ui.definition.{NodeEdges, NodeToAdd, UIProcessObjects}

object UiCodecs extends UiCodecs

trait UiCodecs extends RestModelCodecs {

  import pl.touk.nussknacker.engine.api.typed.TypeEncoders._

  private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] = JsonSumCodecFor(JsonSumCodec.typeField)

  implicit val processActivityCodec: CodecJson[ProcessActivity] = CodecJson.derive[ProcessActivity]

  implicit val objectDefinitionEncode: EncodeJson[ObjectDefinition] = EncodeJson.derive[ObjectDefinition]

  //this has to be derived here, to properly encode list...
  implicit val nodeEdgesEncode: EncodeJson[NodeEdges] = EncodeJson.derive[NodeEdges]

  //this also has to be derived here, to properly encode list...
  implicit def nodesToAddEncode: EncodeJson[NodeToAdd] = EncodeJson.derive[NodeToAdd]

  implicit def processObjectsEncode: EncodeJson[UIProcessObjects] = EncodeJson.derive[UIProcessObjects]

  implicit val grafanaEncode: EncodeJson[MetricsSettings] = EncodeJson.derive[MetricsSettings]

  implicit val userEncodeEncode: EncodeJson[DisplayableUser] = EncodeJson.derive[DisplayableUser]

  //unfortunately, this has do be done manually, as argonaut has problems with recursive types...
  implicit val encodeNodeCount : EncodeJson[NodeCount] = EncodeJson[NodeCount] {
    case NodeCount(all, errors, subprocessCounts) => jObjectFields(
      "all" -> jNumber(all),
      "errors" -> jNumber(errors),
      "subprocessCounts" -> jObjectFields(subprocessCounts.mapValues(encodeNodeCount(_)).toList: _*)
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
    case displayable: Displayable =>
      def safeString(a: String) = Option(a).map(jString).getOrElse(jNull)
      displayable.originalDisplay match {
        case None => jObjectFields("pretty" -> displayable.display)
        case Some(original) => jObjectFields("original" -> safeString(original), "pretty" -> displayable.display)
      }
    case a => jObjectFields("pretty" -> BestEffortJsonEncoder(failOnUnkown = false).encode(a))
  }

  implicit def queryServiceResult: EncodeJson[QueryServiceResult] = {
    implicit val encoder: EncodeJson[Any] = EncodeJson.apply { a =>
      BestEffortJsonEncoder(failOnUnkown = false).encode(a)
    }
    EncodeJson.derive[QueryServiceResult]
  }

  implicit def queryResult: EncodeJson[QueryResult] = {
    implicit val encoder: EncodeJson[Any] = EncodeJson.apply { a =>
      BestEffortJsonEncoder(failOnUnkown = false).encode(a)
    }
    EncodeJson.derive[QueryResult]
  }


}