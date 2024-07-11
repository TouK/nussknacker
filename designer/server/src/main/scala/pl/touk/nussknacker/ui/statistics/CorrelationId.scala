package pl.touk.nussknacker.ui.statistics

import java.util.UUID

class CorrelationId(val value: String) extends AnyVal

object CorrelationId {
  def apply(): CorrelationId =
    new CorrelationId(UUID.randomUUID().toString)
}
