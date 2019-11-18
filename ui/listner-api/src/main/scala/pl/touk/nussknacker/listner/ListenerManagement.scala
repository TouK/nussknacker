package pl.touk.nussknacker.listner

import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.concurrent.ExecutionContext

trait ListenerManagement {
  def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit
}

object ListenerManagement {
  def noop: ListenerManagement = new ListenerManagement {
    override def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = event match {
      case _ => ()
    }
  }
}