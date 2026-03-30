package pl.touk.nussknacker.engine.api.json.encoders

import io.circe.Json
import pl.touk.nussknacker.engine.api.DisplayJson

object ResultsVariableEncoder {

  /**
   * This function has to be run on the engine where is passed proper classLoader. In another case, there may occur a
   * situation where designer with its classLoader tries to encode value loaded by e.g. Flink's classloader and in this
   * case there is classloaders conflict.
   */
  def createEncoderForCurrentClassLoader: Any => Json = {
    // Variables keep some intermediate state of a scenario. We can't be sure that there are only types that are
    // supported for json encoding. It is better to print not-well formatted .toString result than fail in this case.
    lazy val variablesEncoder = new LooseToJsonEncoder(Thread.currentThread().getContextClassLoader)

    def safeString(a: String): Json = Option(a).map(Json.fromString).getOrElse(Json.Null)

    def encode(a: Any): Json = a match {
      case scenarioGraph: DisplayJson =>
        val scenarioGraphJson = scenarioGraph.asJson
        scenarioGraph.originalDisplay match {
          case None           => Json.obj("pretty" -> scenarioGraphJson)
          case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> scenarioGraphJson)
        }
      case null    => Json.Null
      case j: Json => j
      case other   => Json.obj("pretty" -> variablesEncoder.encodeUnsafe(other))
    }

    encode
  }

}
