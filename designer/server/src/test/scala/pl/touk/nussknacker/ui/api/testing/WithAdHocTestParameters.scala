package pl.touk.nussknacker.ui.api.testing

import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

trait WithAdHocTestParameters {

  protected def exampleScenarioSourceId: String

  protected def exampleScenario: CanonicalProcess

  protected def validParameters: TestSourceParameters

  protected def invalidParameters: TestSourceParameters

  protected def parametersProvidedForDryRun: String

  protected def expectedValidationErrorsOnInvalidParametersJson: String

  protected def exampleScenarioGraph: ScenarioGraph = CanonicalProcessConverter.toScenarioGraph(exampleScenario)

}
