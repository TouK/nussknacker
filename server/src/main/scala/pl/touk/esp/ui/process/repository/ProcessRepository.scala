package pl.touk.esp.ui.process.repository

import java.time.LocalDateTime

import argonaut.PrettyParams
import cats.data._
import db.util.DBIOActionInstances._
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.EspError._
import pl.touk.esp.ui.api.ProcessesResources.{ProcessNotInitializedError, UnmarshallError}
import pl.touk.esp.ui.db.migration.CreateDeployedProcessesMigration.DeployedProcessEntityData
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType.ProcessType
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.{ProcessEntityData, ProcessType}
import pl.touk.esp.ui.db.migration.CreateTagsMigration.TagsEntityData
import pl.touk.esp.ui.db.migration.{CreateDeployedProcessesMigration, CreateProcessesMigration, CreateTagsMigration}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.repository.ProcessRepository.{InvalidProcessTypeError, ProcessDetails, ProcessNotFoundError}
import pl.touk.esp.ui.{BadRequestError, EspError, NotFoundError}
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class ProcessRepository(db: JdbcBackend.Database,
                        driver: JdbcProfile,
                        processConverter: ProcessConverter) {

  private val processesMigration = new CreateProcessesMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val deployedProcessesMigration = new CreateDeployedProcessesMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  private val tagsMigration = new CreateTagsMigration {
    override protected val profile: JdbcProfile = ProcessRepository.this.driver
  }

  import deployedProcessesMigration._
  import driver.api._
  import processesMigration._
  import tagsMigration._

  def saveProcess(id: String, processDeploymentData: ProcessDeploymentData)(implicit ec: ExecutionContext): Future[Unit] = {
    val (pType, maybeJson, maybeMainClass) = processDeploymentData match {
      case GraphProcess(json) => (ProcessType.Graph, Some(json), None)
      case CustomProcess(mainClass) => (ProcessType.Custom, None, Some(mainClass))
    }
    val insertOrUpdateAction = processesTable.insertOrUpdate(ProcessEntityData(id, id, None, pType,
      maybeJson, maybeMainClass))
    db.run(insertOrUpdateAction).map(_ => ())
  }

  def fetchProcessesDetails()
                           (implicit ec: ExecutionContext): Future[List[ProcessDetails]] = {
    val action = for {
      tagsForProcesses <- tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))
      processesJoined <- processesTable.joinLeft(deployedProcessesTable)
        .on((p, dp) => p.id === dp.id && dp.deployedAt === deployedProcessesTable.filter(_.id === p.id).map(_.deployedAt).max
      ).result.map(_.map { case (a, b) => createFullDetails(a, b, tagsForProcesses(a.id))})
    } yield processesJoined
    db.run(action).map(_.toList)
  }

  def fetchProcessDetailsById(id: String)
                             (implicit ec: ExecutionContext): Future[Option[ProcessDetails]] = {
    val action =
      for {
        process <- OptionT[DB, ProcessEntityData](processesTable.filter(_.id === id).result.headOption)
        deployedProcess <- OptionT.liftF[DB, Option[DeployedProcessEntityData]](deployedProcessesTable.filter(_.id === id).sortBy(_.deployedAt.desc).result.headOption)
        tags <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === id).result)
      } yield createFullDetails(process, deployedProcess, tags)
    db.run(action.value)
  }

  private def createFullDetails(process: ProcessEntityData,
                                deployedProcessEntityData: Option[DeployedProcessEntityData],
                                tags: Seq[TagsEntityData]): ProcessDetails = {

    ProcessDetails(
      id = process.id,
      name = process.name,
      description = process.description,
      processType = process.processType,
      tags = tags.map(_.name).toList,
      json = process.json.map(json => processConverter.toDisplayableOrDie(json)),
      deployedJson = deployedProcessEntityData.map(proc => processConverter.toDisplayableOrDie(proc.json)),
      deployedAt = deployedProcessEntityData.map(_.deployedAtTime)
    )
  }

  def withParsedProcessById(processId: String)
                           (f: CanonicalProcess => XError[CanonicalProcess])
                           (implicit ec: ExecutionContext): Future[XError[CanonicalProcess]] =
    withProcessJsonById(processId) { optionalCurrentProcessJson =>
      for {
        currentProcessJson <- Xor.fromOption(optionalCurrentProcessJson, ProcessNotInitializedError(processId))
        currentCanonical <- parseProcess(currentProcessJson)
        modifiedProcess <- f(currentCanonical)
      } yield (modifiedProcess, Option(ProcessMarshaller.toJson(modifiedProcess, PrettyParams.nospace)))
    }

  private def parseProcess(canonicalJson: String): Xor[UnmarshallError, CanonicalProcess] =
    ProcessMarshaller.fromJson(canonicalJson)
      .leftMap(e => UnmarshallError(e.msg))
      .toXor

  def withProcessJsonById[T](id: String)
                            (f: Option[String] => XError[(T, Option[String])])
                            (implicit ec: ExecutionContext): Future[XError[T]] = {
    val action = for {
      maybeProcess <- XorT.right[DB, EspError, Option[ProcessEntityData]](processesTable
        .filter(_.id === id).forUpdate.result.headOption)
      existingProcess <- XorT.fromXor[DB](Xor.fromOption(maybeProcess, ProcessNotFoundError(id)))
      _ <- if (existingProcess.processType == ProcessType.Graph) XorT.right[DB, EspError,Unit](dbMonad.pure(()))
            else XorT.left[DB, EspError,Unit](dbMonad.pure(InvalidProcessTypeError(id)))
      transformed <- XorT.fromXor[DB](f(existingProcess.json))
      _ <- XorT.right[DB, EspError, Int](processesTable.filter(_.id === id).map(_.json).update(transformed._2))
    } yield transformed._1
    db.run(action.value.transactionally)
  }

  def fetchProcessDeploymentById(id: String)
                          (implicit ec: ExecutionContext): Future[Option[ProcessDeploymentData]] = {
    val action = processesTable
      .filter(_.id === id)
      .result.headOption
    db.run(action).map(_.flatMap(processDeployment))
  }

  private def processDeployment(process: ProcessEntityData) : Option[ProcessDeploymentData] = (process.json, process.mainClass) match {
    case (Some(json), _) => Some(GraphProcess(json))
    case (None, Some(mainClass)) => Some(CustomProcess(mainClass))
    case _ => None
  }
}

object ProcessRepository {

  case class ProcessDetails(
                             id: String,
                             name: String,
                             description: Option[String],
                             processType: ProcessType,
                             tags: List[String],
                             json: Option[DisplayableProcess],
                             deployedJson: Option[DisplayableProcess],
                             deployedAt: Option[LocalDateTime],
                             isRunning: Boolean = false
                           )

  case class ProcessNotFoundError(id: String) extends NotFoundError {
    def getMessage = s"No process $id found"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }

}