package pl.touk.nussknacker.engine.lite

import cats.effect.IO
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus

trait RunnableScenarioInterpreter extends AutoCloseable {
  def routes: Option[Route]
  def run(): IO[Unit]
  def status(): TaskStatus
}
