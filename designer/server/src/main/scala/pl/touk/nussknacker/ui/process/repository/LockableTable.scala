package pl.touk.nussknacker.ui.process.repository

import db.util.DBIOActionInstances.DB
import slick.lifted.{AbstractTable, TableQuery => LTableQuery}

import scala.concurrent.ExecutionContext

trait LockableTable {

  def withLockedTable[T](
      dbioAction: DB[T]
  ): DB[T]

}

trait DbLockableTable { this: DbioRepository =>

  import profile.api._

  type ENTITY <: AbstractTable[_]

  protected implicit def executionContext: ExecutionContext

  protected def table: LTableQuery[ENTITY]

  def withLockedTable[T](
      dbioAction: DB[T]
  ): DB[T] = for {
    _      <- lockTable
    result <- dbioAction
  } yield result

  private def lockTable: DB[Unit] = {
    run(table.filter(_ => false).forUpdate.result.map(_ => ()))
  }

}
