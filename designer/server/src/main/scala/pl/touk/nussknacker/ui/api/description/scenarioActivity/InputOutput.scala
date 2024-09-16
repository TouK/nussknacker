package pl.touk.nussknacker.ui.api.description.scenarioActivity

import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.NoScenario
import sttp.model.StatusCode.{NotFound, NotImplemented}
import sttp.tapir.EndpointIO.Example
import sttp.tapir.{EndpointOutput, emptyOutputAs, oneOf, oneOfVariantFromMatchType, plainBody}

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
      ),
    )

}
