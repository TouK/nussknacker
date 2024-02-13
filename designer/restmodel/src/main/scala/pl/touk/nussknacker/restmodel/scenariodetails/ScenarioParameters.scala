package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName

@JsonCodec
final case class ScenarioParameters(
    processingMode: ProcessingMode,
    category: String,
    engineSetupName: EngineSetupName
)
