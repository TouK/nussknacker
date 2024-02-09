package pl.touk.nussknacker.tests.utils.scala

import db.util.DBIOActionInstances.DB
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner

trait DBIOActionValues { self: ScalaFutures =>

  protected def dbioRunner: DBIOActionRunner

  implicit class DBIOActionOps[T](dbioAction: DB[T]) {

    def dbioActionValues: T = dbioRunner.run(dbioAction).futureValue

  }

}
