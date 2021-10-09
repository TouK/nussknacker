package pl.touk.nussknacker.engine.standalone.api

import io.circe.Json
import pl.touk.nussknacker.engine.standalone.api.StandaloneScenarioEngineTypes.GenericResultType


trait ResponseEncoder[-Input] {

  def toJsonResponse(input: Input, result: List[Any]): GenericResultType[Json]

}
