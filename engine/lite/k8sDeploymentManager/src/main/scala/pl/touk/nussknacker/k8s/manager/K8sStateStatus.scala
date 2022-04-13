package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.deployment._

object K8sStateStatus  {
  val MultipleJobsRunning: StateStatus = NotEstablishedStateStatus("More than one deployment running")

  def failed(ex: Throwable): StateStatus = DetailedFailedStateStatus(ex.getMessage)

  case class DetailedFailedStateStatus(message: String) extends CustomStateStatus("Failed") {
    override def isFailed: Boolean = true
  }
}
