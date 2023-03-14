package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._

import java.net.URI

object K8sStateStatus  {
  val MultipleJobsRunning: StateStatus = NotEstablishedStateStatus("MULTIPLE_JOBS_RUNNING")

  val customStateDefinitions: Map[StatusName, StateDefinitionDetails] = Map(
    MultipleJobsRunning.name -> StateDefinitionDetails(
      displayableName = "More than one deployment running",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("More than one deployment running"),
      description = Some("More than one deployment running")
    )
  )
}
