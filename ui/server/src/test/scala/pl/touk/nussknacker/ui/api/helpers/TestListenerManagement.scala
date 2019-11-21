package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.ui.listener.{ListenerManagement, ProcessChangeEvent}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class TestListenerManagement extends ListenerManagement {
  override def handler(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = TestListenerManagement.add(event)
}

object TestListenerManagement {
  var events: List[ProcessChangeEvent] = Nil
  def clear(): Unit = {
    events = Nil
  }
  def add(event: ProcessChangeEvent): Unit = {
    events = event :: events
  }
}