package pl.touk.nussknacker.test.mock

import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.ExecutionContext

class TestProcessChangeListener extends ProcessChangeListener {

  val events: ConcurrentLinkedQueue[ProcessChangeEvent] = new ConcurrentLinkedQueue[ProcessChangeEvent]()

  def clear(): Unit = {
    events.clear()
  }

  private def add(event: ProcessChangeEvent): Unit = {
    events.add(event)
  }

  override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = add(event)
}
