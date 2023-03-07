package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment._

import java.net.URI

object K8sStateStatus  {
  val MultipleJobsRunning: StateStatus = NotEstablishedStateStatus("MULTIPLE_JOBS_RUNNING")

//  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)
//
//  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus("Failed") {
//    override def isFailed: Boolean = true
//  }

  val customStateDefinitions: Set[StateDefinition] = Set(
    StateDefinition(
      name = MultipleJobsRunning.name,
      displayableName = "More than one deployment running",
      icon = Some(URI.create("/assets/states/error.svg")),
      tooltip = Some("More than one deployment running"),
      description = Some("More than one deployment running")
    )
  )
}
