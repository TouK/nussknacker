package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters

final case class ScenarioParametersWithEngineSetupErrors(
    parameters: ScenarioParameters,
    engineSetupErrors: List[String]
)
