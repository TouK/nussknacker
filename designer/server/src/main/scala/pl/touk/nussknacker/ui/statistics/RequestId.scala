package pl.touk.nussknacker.ui.statistics

import java.util.UUID

class RequestId(val value: String) extends AnyVal

object RequestId {
  def apply(): RequestId =
    new RequestId(UUID.randomUUID().toString)
}
