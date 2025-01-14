package pl.touk.nussknacker.engine.api.deployment.periodic.model

import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion}

case class PeriodicProcessDetails(
    processVersion: ProcessVersion,
    processMetaData: MetaData,
    inputConfigDuringExecutionJson: String,
)
