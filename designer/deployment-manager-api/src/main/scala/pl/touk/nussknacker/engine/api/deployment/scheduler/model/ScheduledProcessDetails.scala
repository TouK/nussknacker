package pl.touk.nussknacker.engine.api.deployment.scheduler.model

import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}

case class ScheduledProcessDetails(
    processVersion: ProcessVersion,
    processMetaData: MetaData,
    inputConfigDuringExecutionJson: String,
)
