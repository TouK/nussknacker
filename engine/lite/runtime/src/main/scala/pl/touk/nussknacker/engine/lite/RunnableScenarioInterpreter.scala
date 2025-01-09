package pl.touk.nussknacker.engine.lite

import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import akka.http.scaladsl.server.Route
import cats.effect.IO

trait RunnableScenarioInterpreter extends AutoCloseable {
  def routes: Option[Route]
  def run(): IO[Unit]
  def status(): TaskStatus
}
