package pl.touk.nussknacker.ui.limits

import pl.touk.nussknacker.ui.limits.GlobalLimitsConfig.MaxActiveScenariosCount

final case class GlobalLimitsConfig(maxActiveScenariosCount: Option[MaxActiveScenariosCount])

object GlobalLimitsConfig {
  final case class MaxActiveScenariosCount(value: Int) extends AnyVal

  val default: GlobalLimitsConfig = GlobalLimitsConfig(maxActiveScenariosCount = None)
}
