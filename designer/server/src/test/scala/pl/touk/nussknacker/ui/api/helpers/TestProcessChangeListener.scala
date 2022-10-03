package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}

import scala.concurrent.ExecutionContext

class TestProcessChangeListener extends ProcessChangeListener {

  var events: List[ProcessChangeEvent] = List.empty

  def clear(): Unit = {
    events = List.empty
  }

  private def add(event: ProcessChangeEvent): Unit = {
    events = event :: events
  }

  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = add(event)
}
