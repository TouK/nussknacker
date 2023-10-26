package pl.touk.nussknacker.restmodel.scenariodetails

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.deployment.ProcessAction
import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant

@JsonCodec final case class ScenarioVersion(
    processVersionId: VersionId,
    createDate: Instant,
    user: String,
    modelVersion: Option[Int],
    actions: List[ProcessAction]
)
