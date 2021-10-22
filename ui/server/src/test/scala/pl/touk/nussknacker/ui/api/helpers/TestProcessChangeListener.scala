package pl.touk.nussknacker.ui.api.helpers

import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}

import scala.concurrent.ExecutionContext

class TestProcessChangeListener extends ProcessChangeListener {
  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = TestProcessChangeListener.add(event)
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