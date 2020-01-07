package pl.touk.nussknacker.ui.config

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.StateStatus

object ProcessStateConfig {
  def managersIcons(managers: Map[ProcessingType, ProcessingTypeData]): Map[String, Map[ProcessingType, String]] =
    managers.map({
      case (mk, mv) => (mk.toString, mapStateStatus(mv.processManager.processStateConfigurator.statusIcons))
    })

  def managersTooltips(managers: Map[ProcessingType, ProcessingTypeData]): Map[String, Map[ProcessingType, String]] =
    managers.map({
      case (mk, mv) => (mk.toString, mapStateStatus(mv.processManager.processStateConfigurator.statusTooltips))
    })

  private def mapStateStatus(state: Map[StateStatus, String]): Map[String, String] = state.map {
    case (ik, iv) => (ik.toString, iv)
  }
}
