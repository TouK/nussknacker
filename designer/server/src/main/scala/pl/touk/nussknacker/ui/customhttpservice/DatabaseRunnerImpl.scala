package pl.touk.nussknacker.ui.customhttpservice

import pl.touk.nussknacker.ui.customhttpservice.services.DatabaseRunner
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner

import scala.concurrent.Future

final class DatabaseRunnerImpl(val dbioRunner: DBIOActionRunner) extends DatabaseRunner {
  override def runInTransaction[T](action: DB[T]): Future[T] = dbioRunner.runInTransaction(action)
  override def run[T](action: DB[T]): Future[T]              = dbioRunner.run(action)
}
