package pl.touk.nussknacker.ui.listener

import pl.touk.nussknacker.engine.api.deployment.User
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.ExecutionContext

trait ProcessChangeListener {
  def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit
}

object ProcessChangeListener {
  def noop: ProcessChangeListener = new ProcessChangeListener {
    override def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit = event match {
      case _ => ()
    }
  }
}