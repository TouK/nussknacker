package pl.touk.nussknacker.engine.api.deployment

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.deployment.User

@JsonCodec class ActionRequest(
    name: ScenarioActionName,
    processVersion: ProcessVersion,
    user: User,
    params: Map[String, String]
)
