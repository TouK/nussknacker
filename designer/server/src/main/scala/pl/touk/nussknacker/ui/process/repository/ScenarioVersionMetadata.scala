package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.process.VersionId

import java.time.Instant

final case class ScenarioVersionMetadata(
    versionId: VersionId,
    createdAt: Instant,
    createdByUser: String,
)
