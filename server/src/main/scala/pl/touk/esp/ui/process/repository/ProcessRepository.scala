package pl.touk.esp.ui.process.repository

import cats.data._
import db.util.DBIOActionInstances.{DB, _}
import pl.touk.esp.ui.EspError._
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessEntityData
import pl.touk.esp.ui.db.migration.{CreateProcessesMigration, CreateTagsMigration}
import pl.touk.esp.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessNotFoundError}
import pl.touk.esp.ui.{EspError, NotFoundError}
import slick.dbio.Effect.Read
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcProfile) {

  private val processesMigration = new CreateProcessesMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val tagsMigration = new CreateTagsMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  import driver.api._
  import processesMigration._
  import tagsMigration._

  def saveProcess(id: String, json: String)(implicit ec: ExecutionContext): Future[Unit] = {
    val insertOrUpdateAction = processesTable.insertOrUpdate(ProcessEntityData(id, id, None, Some(json)))
    db.run(insertOrUpdateAction).map(_ => ())
  }

  def fetchProcessesDetails()
                           (implicit ec: ExecutionContext): Future[List[ProcessDetails]] = {
    val action = for {
      processes <- processesTable.result
      processesWithTags <- DBIO.sequence(processes.map(fetchTagsThanPrepareDetailsAction))
    } yield processesWithTags
    db.run(action).map(_.toList)
  }

  private def fetchTagsThanPrepareDetailsAction(process: ProcessEntityData)
                                               (implicit ec: ExecutionContext): DBIOAction[ProcessDetails, NoStream, Read] = {
    fetchProcessTagsByIdAction(process.id).map { tagsForProcess =>
      ProcessDetails(process.id, process.name, process.description, tagsForProcess)
    }
  }

  def fetchProcessDetailsById(id: String)
                             (implicit ec: ExecutionContext): Future[Option[ProcessDetails]] = {
    val action =
      for {
        process <- OptionT[DB, ProcessEntityData](processesTable.filter(_.id === id).result.headOption)
        processWithTags <- OptionT.liftF[DB, ProcessDetails](fetchTagsThanPrepareDetailsAction(process))
      } yield processWithTags
    db.run(action.value)
  }

  def withProcessJsonById[T](id: String)
                            (f: Option[String] => XError[(T, Option[String])])
                            (implicit ec: ExecutionContext): Future[XError[T]] = {

    val action = for {
      maybeProcess <- XorT.right[DB, EspError, Option[Option[String]]](processesTable.filter(_.id === id).forUpdate.map(_.json).result.headOption)
      existingProcess <- XorT.fromXor[DB](Xor.fromOption(maybeProcess, ProcessNotFoundError(id)))
      transformed <- XorT.fromXor[DB](f(existingProcess))
      _ <- XorT.right[DB, EspError, Int](processesTable.filter(_.id === id).map(_.json).update(transformed._2))
    } yield transformed._1
    db.run(action.value.transactionally)
  }

  private def fetchProcessTagsByIdAction(processId: String)
                                        (implicit ec: ExecutionContext): DBIOAction[List[String], NoStream, Read] =
    tagsTable.filter(_.processId === processId).map(_.name).result.map(_.toList)

  def fetchProcessJsonById(id: String)
                          (implicit ec: ExecutionContext): Future[Option[String]] = {
    val action = processesTable.filter(_.id === id).map(_.json).result.headOption.map(_.flatten)
    db.run(action)
  }

}

object ProcessRepository {

  case class ProcessDetails(id: String, name: String, description: Option[String], tags: List[String])

  case class ProcessNotFoundError(id: String) extends NotFoundError {
    def getMessage = s"No process $id found"
  }

}