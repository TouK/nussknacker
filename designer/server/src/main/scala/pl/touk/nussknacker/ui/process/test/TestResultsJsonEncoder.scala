package pl.touk.nussknacker.ui.process.test

import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object TestResultsJsonEncoder {

  import pl.touk.nussknacker.engine.api.CirceUtil._

  import io.circe.syntax._

  /**
   * NU-1455: This function has to be run on the engine where is passed proper classLoader. In another case, there may
   * occur a situation where designer with its classLoader tries to encode value with e.g. is loaded by Flink's encoder
   * and in this case there is loaders conflict
   */
  val testResultsVariableEncoder: Any => io.circe.Json = {
    case scenarioGraph: DisplayJson =>
      def safeString(a: String): Json = Option(a).map(Json.fromString).getOrElse(Json.Null)

      val scenarioGraphJson = scenarioGraph.asJson
      scenarioGraph.originalDisplay match {
        case None           => Json.obj("pretty" -> scenarioGraphJson)
        case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> scenarioGraphJson)
      }
    case null    => Json.Null
    case j: Json => j
    case a =>
      Json.obj("pretty" -> {
        BestEffortJsonEncoder(failOnUnknown = false, a.getClass.getClassLoader).encode(a)
      })
  }

  private implicit val resultsWithCountsEncoder: Encoder[ResultsWithCounts[Json]] = deriveConfiguredEncoder

  private implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[ResultContext[Json]]                              = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder

    // TODO: do we want more information here?
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(nodeResults, invocationResults, externalInvocationResults, exceptions, _) =>
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

  def encode(result: ResultsWithCounts[Json]): Json =
    result.asJson

}
