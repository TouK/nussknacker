package pl.touk.nussknacker.engine.testmode

import io.circe.Json
import pl.touk.nussknacker.engine.api.DisplayJson
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object TestInterpreterRunner {

  /**
   * NU-1455: This function has to be run on the engine where is passed proper classLoader. In another case, there may
   * occur a situation where designer with its classLoader tries to encode value with e.g. is loaded by Flink's encoder
   * and in this case there is loaders conflict
   */
  private[testmode] def testResultsVariableEncoder: Any => io.circe.Json = {
    lazy val encoder = BestEffortJsonEncoder(failOnUnknown = false, Thread.currentThread().getContextClassLoader)
    def encode(a: Any): Json = a match {
      case scenarioGraph: DisplayJson =>
        def safeString(a: String): Json = Option(a).map(Json.fromString).getOrElse(Json.Null)

        val scenarioGraphJson = scenarioGraph.asJson
        scenarioGraph.originalDisplay match {
          case None           => Json.obj("pretty" -> scenarioGraphJson)
          case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> scenarioGraphJson)
        }
      case null    => Json.Null
      case j: Json => j
      case a       => Json.obj("pretty" -> encoder.encode(a))
    }
    encode
  }

}
