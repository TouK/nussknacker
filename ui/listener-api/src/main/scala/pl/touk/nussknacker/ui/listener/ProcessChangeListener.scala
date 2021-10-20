package pl.touk.nussknacker.ui.listener

import pl.touk.nussknacker.ui.listener.services.ListenerUser
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.ExecutionContext

trait ProcessChangeListener {
  def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: ListenerUser): Unit
}

object ProcessChangeListener {
  def noop: ProcessChangeListener = new ProcessChangeListener {
    override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: ListenerUser): Unit = event match {
      case _ => ()
    }
  }
}