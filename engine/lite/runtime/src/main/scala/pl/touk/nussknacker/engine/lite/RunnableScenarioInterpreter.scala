package pl.touk.nussknacker.engine.lite

import org.apache.pekko.http.scaladsl.server.Route
import cats.effect.IO
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus

trait RunnableScenarioInterpreter extends AutoCloseable {
  def routes: Option[Route]
  def run(): IO[Unit]
  def status(): TaskStatus
}
