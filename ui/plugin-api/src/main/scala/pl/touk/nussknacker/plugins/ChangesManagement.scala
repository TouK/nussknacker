package pl.touk.nussknacker.plugins

import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

trait ChangesManagement {
  def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit
}

object ChangesManagement {
  def noop: ChangesManagement = new ChangesManagement {
    override def handler(event: ChangeEvent)(implicit ec: ExecutionContext, user: LoggedUser): Unit = event match {
      case _ => ()
    }
  }
}