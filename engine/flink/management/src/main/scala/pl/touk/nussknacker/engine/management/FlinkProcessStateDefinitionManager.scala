package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{OverridingProcessStateDefinitionManager, ProcessState}
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.JobOverview

object FlinkProcessStateDefinitionManager extends OverridingProcessStateDefinitionManager(
  delegate = SimpleProcessStateDefinitionManager,
  statusActionsPF = FlinkStateStatus.statusActionsPF
){

  def errorMultipleJobsRunning(duplicates: List[JobOverview]): ProcessState = {
    processState(ProblemStateStatus.multipleJobsRunning)
      .copy(
        deploymentId = Some(ExternalDeploymentId(duplicates.head.jid)),
        startTime = Some(duplicates.head.`start-time`),
        errors = List(s"Expected one job, instead: ${duplicates.map(job => s"${job.jid} - ${job.state}").mkString(", ")}")
      )
  }

}
