package pl.touk.nussknacker.ui.process.test

import io.circe.{Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{Context, DisplayJson}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExpressionInvocationResult,
  ExternalInvocationResult,
  TestResults
}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object TestResultsJsonEncoder {
  def apply(modelData: ModelData): TestResultsJsonEncoder =
    new TestResultsJsonEncoder(modelData.modelClassLoader.classLoader)
}

final class TestResultsJsonEncoder(classLoader: ClassLoader) {

  private val jsonEncoder = BestEffortJsonEncoder(failOnUnknown = false, classLoader)

  import pl.touk.nussknacker.engine.api.CirceUtil._

  import io.circe.syntax._

  private implicit val resultsWithCountsEncoder: Encoder[ResultsWithCounts] = deriveConfiguredEncoder

  private implicit val testResultsEncoder: Encoder[TestResults] = new Encoder[TestResults]() {

    private implicit val anyEncoder: Encoder[Any] = {
      case scenarioGraph: DisplayJson =>
        def safeString(a: String) = Option(a).map(Json.fromString).getOrElse(Json.Null)

        val scenarioGraphJson = scenarioGraph.asJson
        scenarioGraph.originalDisplay match {
          case None           => Json.obj("pretty" -> scenarioGraphJson)
          case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> scenarioGraphJson)
        }
      case null => Json.Null
      case a    => Json.obj("pretty" -> jsonEncoder.encode(a))
    }

    // TODO: do we want more information here?
    private implicit val contextEncoder: Encoder[Context] = (a: Context) =>
      Json.obj(
        "id"        -> Json.fromString(a.id),
        "variables" -> a.variables.asJson
      )

    private val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))

    // It has to be done manually, deriveConfiguredEncoder doesn't work properly with value: Any
    private implicit val externalInvocationResultEncoder: Encoder[ExternalInvocationResult] =
      (value: ExternalInvocationResult) =>
        Json.obj(
          "name"      -> Json.fromString(value.name),
          "contextId" -> Json.fromString(value.contextId),
          "value"     -> value.value.asJson,
        )

    // It has to be done manually, deriveConfiguredEncoder doesn't work properly with value: Any
    private implicit val expressionInvocationResultEncoder: Encoder[ExpressionInvocationResult] =
      (value: ExpressionInvocationResult) =>
        Json.obj(
          "name"      -> Json.fromString(value.name),
          "contextId" -> Json.fromString(value.contextId),
          "value"     -> value.value.asJson,
        )

    private implicit val exceptionsEncoder: Encoder[NuExceptionInfo[_ <: Throwable]] =
      (value: NuExceptionInfo[_ <: Throwable]) =>
        Json.obj(
          // We don't need componentId on the FE here
          "nodeId"    -> value.nodeComponentInfo.map(_.nodeId).asJson,
          "throwable" -> throwableEncoder(value.throwable),
          "context"   -> value.context.asJson
        )

    override def apply(a: TestResults): Json = a match {
      case TestResults(nodeResults, invocationResults, externalInvocationResults, exceptions) =>
        Json.obj(
          "nodeResults"       -> nodeResults.map { case (node, list) => node -> list.sortBy(_.id) }.asJson,
          "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id).asJson
        )
    }

  }

  def encode(result: ResultsWithCounts): Json =
    result.asJson

}
