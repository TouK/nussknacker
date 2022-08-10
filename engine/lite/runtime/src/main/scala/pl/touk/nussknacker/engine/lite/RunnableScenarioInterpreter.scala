package pl.touk.nussknacker.engine.lite

import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus

import scala.concurrent.Future

trait RunnableScenarioInterpreter extends AutoCloseable {

  def run(): Future[Unit]

  def status(): TaskStatus

}
