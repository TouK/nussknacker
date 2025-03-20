package pl.touk.nussknacker.ui.listener

import scala.concurrent.ExecutionContext

trait ProcessChangeListener {
  def handle(event: ProcessChangeEvent)(implicit ec: ExecutionContext, user: User): Unit
}
