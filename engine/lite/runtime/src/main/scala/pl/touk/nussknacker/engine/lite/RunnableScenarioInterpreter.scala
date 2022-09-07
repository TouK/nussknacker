package pl.touk.nussknacker.engine.lite

import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

trait RunnableScenarioInterpreter extends AutoCloseable {
  def routes: Option[Route]
  def run(): Future[Unit]
  def status(): TaskStatus
}