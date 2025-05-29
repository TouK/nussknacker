package pl.touk.nussknacker.restmodel

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.VersionId

@JsonCodec
final case class DeployResponse(deployedScenarioVersionId: VersionId)
