package pl.touk.nussknacker.development.manager

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.deployment.{CustomStateStatus, NotEstablishedStateStatus, ProcessActionType, ProcessStateDefinitionManager, StateStatus}

import java.net.URI

case object AfterRunningStatus extends CustomStateStatus("AFTER") {
  override def isRunning: Boolean = true
}

case object PreparingResourcesStatus extends CustomStateStatus("PREPARING")

case object TestStatus extends CustomStateStatus("TEST")

class DevelopmentProcessStateDefinitionManager(delegate: ProcessStateDefinitionManager) extends ProcessStateDefinitionManager {

  override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = stateStatus match {
    case AfterRunningStatus => List(ProcessActionType.Cancel)
    case PreparingResourcesStatus => List(ProcessActionType.Deploy)
    case TestStatus => List(ProcessActionType.Deploy)
    case _ => delegate.statusActions(stateStatus)
  }

  override def statusTooltip(stateStatus: StateStatus): Option[String] =
    statusDescription(stateStatus)

  override def statusDescription(stateStatus: StateStatus): Option[String] = stateStatus match {
    case AfterRunningStatus => Some(s"External running.")
    case PreparingResourcesStatus => Some(s"Preparing external resources.")
    case TestStatus => Some(s"Run testing mode.")
    case _ => delegate.statusDescription(stateStatus)
  }

  override def statusIcon(stateStatus: StateStatus): Option[URI] =
    delegate.statusIcon(stateStatus)

  override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus =
    delegate.mapActionToStatus(stateAction)
}
