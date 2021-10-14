package pl.touk.nussknacker.engine.standalone

import io.circe.Json
import pl.touk.nussknacker.engine.standalone.api.ResponseEncoder
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object DefaultResponseEncoder extends ResponseEncoder[Any] {

  private val bestEffortEncoder = BestEffortJsonEncoder(failOnUnkown = true, getClass.getClassLoader)

  override def toJsonResponse(input: Any, result: List[Any]): Json = bestEffortEncoder.encode(result)

}
