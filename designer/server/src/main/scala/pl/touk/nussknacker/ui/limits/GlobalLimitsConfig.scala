package pl.touk.nussknacker.ui.limits

import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig.ActiveScenariosLimit

final case class GlobalLimitsConfig(activeScenariosLimit: Option[ActiveScenariosLimit])

object GlobalLimitsConfig {
  final case class ActiveScenariosLimit(value: Int) extends AnyVal

  val default: GlobalLimitsConfig = GlobalLimitsConfig(activeScenariosLimit = None)
}
