package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.NoScenario
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{
  PaginationContext,
  ScenarioActivityError,
  ScenarioActivityType
}
import sttp.model.StatusCode.NotFound
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{EndpointInput, EndpointOutput, oneOf, oneOfVariantFromMatchType, plainBody, query}

object InputOutput {

  val scenarioNotFoundErrorOutput: EndpointOutput.OneOf[ScenarioActivityError, ScenarioActivityError] =
    oneOf[ScenarioActivityError](
      oneOfVariantFromMatchType(
        NotFound,
        plainBody[NoScenario]
          .example(
            Example.of(
              summary = Some("No scenario {scenarioName} found"),
              value = NoScenario(ProcessName("'example scenario'"))
            )
          )
      )
    )

  val paginationContextInput: EndpointInput[PaginationContext] =
    query[Long]("pageSize")
      .and(query[Long]("pageNumber"))
      .map(PaginationContext.tupled.apply(_))(PaginationContext.unapply(_).get)

  val searchTextInput: EndpointInput[String] =
    query[String]("searchText")

  val activityTypeFilterInput: EndpointInput[List[ScenarioActivityType]] =
    query[List[ScenarioActivityType]]("activityType")

}
