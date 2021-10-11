package pl.touk.nussknacker.engine.standalone.api

import io.circe.Json
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.GenericResultType


trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): GenericResultType[Json]

}
