package pl.touk.nussknacker.restmodel.scenariodetails

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName

case class ScenarioParameters(
    processingMode: ProcessingMode,
    category: String,
    engineSetupName: EngineSetupName
)

case class ScenarioParametersWithEngineSetupErrors(
    parameters: ScenarioParameters,
    engineSetupErrors: List[String]
)
