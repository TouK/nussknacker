package pl.touk.nussknacker.ui.config

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus

object ProcessStateConfig {
  def managersIcons(managers: Map[ProcessingType, ProcessingTypeData]): Map[String, Option[Map[ProcessingType, String]]] =
    managers.map({
      case (mk, mv) => (mk.toString, mv.processManager.processStateConfigurator.statusIcons.map(mapStateStatus))
    })

  def managersTooltips(managers: Map[ProcessingType, ProcessingTypeData]): Map[String, Option[Map[ProcessingType, String]]] =
    managers.map({
      case (mk, mv) => (mk.toString, mv.processManager.processStateConfigurator.statusMessages.map(mapStateStatus))
    })

  private def mapStateStatus(state: Map[StateStatus, String]): Map[String, String] = state.map {
    case (ik, iv) => (ik.toString, iv)
  }


}
