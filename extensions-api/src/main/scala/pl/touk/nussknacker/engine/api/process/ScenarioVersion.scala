package pl.touk.nussknacker.engine.api.process

import io.circe.generic.JsonCodec

import java.time.Instant

@JsonCodec final case class ScenarioVersion(
    processVersionId: VersionId,
    createDate: Instant,
    user: String
)
