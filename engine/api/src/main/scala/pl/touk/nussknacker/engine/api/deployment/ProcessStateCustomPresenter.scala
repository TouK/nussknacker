package pl.touk.nussknacker.engine.api.deployment
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus

object ProcessStateCustomPresenter extends ProcessStatePresenter {
  override def presentTooltipMessage(status: StateStatus): String = ProcessStateCustoms.getStatusTooltipMessage(status)
  override def presentIcon(status: StateStatus): String = ProcessStateCustoms.getStatusIcon(status)
}
