package pl.touk.esp.ui.process.repository

import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessEntityData
import pl.touk.esp.ui.db.migration.{CreateProcessesMigration, CreateTagsMigration}
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import slick.dbio.Effect.Read
import slick.driver.JdbcDriver
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}

class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcDriver) {

  private val processesMigration = new CreateProcessesMigration {
    override protected val driver: JdbcDriver = ProcessRepository.this.driver
  }

  private val tagsMigration = new CreateTagsMigration {
    override protected val driver: JdbcDriver = ProcessRepository.this.driver
  }

  import processesMigration._
  import tagsMigration._
  import driver.api._

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

  private def fetchProcessTagsByIdAction(processId: String)
                                        (implicit ec: ExecutionContext): DBIOAction[List[String], NoStream, Read] =
    tagsTable.filter(_.processId === processId).map(_.name).result.map(_.toList)

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

  def fetchProcessJsonById(id: String)
                          (implicit ec: ExecutionContext): Future[Option[String]] = {
    val action = processesTable.filter(_.id === id).map(_.json).result.headOption.map(_.flatten)
    db.run(action)
  }


}

object ProcessRepository {

  case class ProcessDetails(id: String, name: String, description: Option[String], tags: List[String])


}