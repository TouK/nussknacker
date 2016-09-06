package pl.touk.esp.ui.process.repository

import cats.data.{Kleisli, Validated, Writer, WriterT}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessEntityData
import pl.touk.esp.ui.db.migration.{CreateProcessesMigration, CreateTagsMigration}
import pl.touk.esp.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessNotFoundError}
import slick.dbio.Effect.Read
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}

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
        optionalProcess <- processesTable.filter(_.id === id).result.headOption
        optionalProcessWithTags <- optionalProcess match {
          case (Some(process)) =>
            fetchTagsThanPrepareDetailsAction(process).map(Some(_))
          case None =>
            DBIO.successful(None)
        }
      } yield optionalProcessWithTags
    db.run(action)
  }

  def withProcessJsonById[T](id: String)
                            (f: Option[String] => Writer[T, Option[String]])
                            (implicit ec: ExecutionContext): Future[Validated[ProcessNotFoundError, Writer[T, Unit]]] = {
    val action = for {
      optionalProcessJson <- processesTable.filter(_.id === id).forUpdate.map(_.json).result.headOption
      updateResult <- optionalProcessJson match {
        case Some(existingProcess) =>
          val (out, action) = f(existingProcess).map { newProcess =>
            processesTable.filter(_.id === id).map(_.json).update(newProcess)
          }.run // TODO: da się to jakimś Kleisli zrobić?
          action.map { _ =>
            Valid(Writer.tell(out))
          }
        case None =>
          DBIO.successful(Invalid(ProcessNotFoundError(id)))
      }
    } yield updateResult
    db.run(action.transactionally)
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

  case class ProcessNotFoundError(id: String) extends Exception(s"Process $id not found")

}