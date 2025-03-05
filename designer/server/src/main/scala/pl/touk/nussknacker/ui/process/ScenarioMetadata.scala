package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessingType, ProcessName}
import pl.touk.nussknacker.ui.process.label.ScenarioLabel

import java.sql.Timestamp

final case class ScenarioMetadata(
    id: ProcessId,
    name: ProcessName,
    description: Option[String],
    processCategory: String,
    processingType: ProcessingType,
    isFragment: Boolean,
    isArchived: Boolean,
    createdAt: Timestamp,
    createdBy: String,
    labels: List[ScenarioLabel],
    impersonatedByIdentity: Option[String],
    impersonatedByUsername: Option[String]
)
