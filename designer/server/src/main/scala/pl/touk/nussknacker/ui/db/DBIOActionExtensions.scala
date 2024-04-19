package pl.touk.nussknacker.ui.db

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner

import scala.concurrent.Future

trait DBIOActionExtensions {

  implicit class RunExtension[T](action: DB[T]) {
    def run(implicit runner: DBIOActionRunner): Future[T] =
      runner.run(action)

    def runInTransaction(implicit runner: DBIOActionRunner): Future[T] =
      runner.runInTransaction(action)
  }

}
