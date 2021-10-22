package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.ui.listener.services.ListenerUser
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class TestProcessChangeListener extends ProcessChangeListener {
  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: ListenerUser): Unit = TestProcessChangeListener.add(event)
}

object TestProcessChangeListener {
  var events: List[ProcessChangeEvent] = List.empty
  def clear(): Unit = {
    events = List.empty
  }
  def add(event: ProcessChangeEvent): Unit = {
    events = event :: events
  }
}