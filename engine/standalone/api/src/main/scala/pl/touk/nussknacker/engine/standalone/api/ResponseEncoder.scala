package pl.touk.nussknacker.engine.standalone.api

import io.circe.Json
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType


trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): GenericResultType[Json]

}
