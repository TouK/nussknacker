package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import cats.data.OptionT
import cats.instances.future._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.{DB, _}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DBFetchingProcessRepository {
  def create(dbConfig: DbConfig)(implicit ec: ExecutionContext) =
    new DBFetchingProcessRepository[DB](dbConfig) with DbioRepistory

  def createFutureRespository(dbConfig: DbConfig)(implicit ec: ExecutionContext) =
    new DBFetchingProcessRepository[Future](dbConfig) with BasicRepository

}

abstract class DBFetchingProcessRepository[F[_]: Monad](val dbConfig: DbConfig) extends FetchingProcessRepository[F] with LazyLogging {

  import api._

  override def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](query: FetchProcessesDetailsQuery)
                                                                   (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[BaseProcessDetails[PS]]] = {
    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      query.isSubprocess.map(arg => process => process.isSubprocess === arg),
      query.isArchived.map(arg => process => process.isArchived === arg),
      query.categories.map(arg => process => process.processCategory.inSet(arg)),
      query.processingTypes.map(arg => process => process.processingType.inSet(arg)),
      query.names.map(arg => process => process.name.inSet(arg)),
    )

    run(fetchProcessDetailsByQueryAction({ process =>
      expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process))
    }, query.isDeployed))
  }

  private def fetchProcessDetailsByQueryAction[PS: ProcessShapeFetchStrategy](query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
                                                                              isDeployed: Option[Boolean])(implicit loggedUser: LoggedUser, ec: ExecutionContext): DBIOAction[List[BaseProcessDetails[PS]], NoStream, Effect.All with Effect.Read] = {
    (for {
      lastActionPerProcess <- fetchLastFinishedActionPerProcessQuery.result
      lastDeployedActionPerProcess <- fetchLastDeployedActionPerProcessQuery.result
      latestProcesses <- fetchLatestProcessesQuery(query, lastDeployedActionPerProcess, isDeployed).result
    } yield
      latestProcesses.map { case ((_, processVersion), process) => createFullDetails(
        process,
        processVersion,
        lastActionPerProcess.find(_._1 == process.id).map(_._2),
        lastDeployedActionPerProcess.find(_._1 == process.id).map(_._2),
        isLatestVersion = true
      )}).map(_.toList)
  }

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]] = {
    run(fetchLatestProcessDetailsForProcessIdQuery(id))
  }

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)
                                                                      (implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[BaseProcessDetails[PS]]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](fetchProcessLatestVersionsQuery(processId)(ProcessShapeFetchStrategy.NotFetch).result.headOption)
      processVersion <- OptionT[DB, ProcessVersionEntityData](fetchProcessLatestVersionsQuery(processId).filter(pv => pv.id === versionId).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(processVersion, isLatestVersion = latestProcessVersion.id == processVersion.id)
    } yield processDetails
    run(action.value)
  }

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]] = {
    run(processesTable.filter(_.name === processName).map(_.id).result.headOption.map(_.map(id => id)))
  }

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]] = {
    run(processesTable.filter(_.id === processId).map(_.name).result.headOption)
  }

  override def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessEntityData]] = {
    run(processesTable.filter(_.name === processName).result.headOption)
  }

  override def fetchProcessActions(processId: ProcessId)(implicit ec: ExecutionContext): F[List[ProcessAction]] =
    run(fetchProcessLatestFinishedActionsQuery(processId).result.map(_.toList.map(ProcessDBQueryRepository.toProcessAction)))

  override def fetchProcessingType(processId: ProcessId)(implicit user: LoggedUser, ec: ExecutionContext): F[ProcessingType] = {
    run {
      implicit val fetchStrategy: ProcessShapeFetchStrategy[_] = ProcessShapeFetchStrategy.NotFetch
      fetchLatestProcessDetailsForProcessIdQuery(processId).flatMap {
        case None => DBIO.failed(ProcessNotFoundError(processId.value.toString))
        case Some(process) => DBIO.successful(process.processingType)
      }
    }
  }

  private def fetchLatestProcessDetailsForProcessIdQuery[PS: ProcessShapeFetchStrategy](id: ProcessId)
                                                                                       (implicit loggedUser: LoggedUser, ec: ExecutionContext): DB[Option[BaseProcessDetails[PS]]] = {
    (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](fetchProcessLatestVersionsQuery(id).result.headOption)
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true)
    } yield processDetails).value
  }

  private def fetchProcessDetailsForVersion[PS: ProcessShapeFetchStrategy](processVersion: ProcessVersionEntityData, isLatestVersion: Boolean)
                                                                          (implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](fetchProcessLatestVersionsQuery(id)(ProcessShapeFetchStrategy.NotFetch).result)
      actions <- OptionT.liftF[DB, Seq[(ProcessActionEntityData, Option[CommentEntityData])]](fetchProcessLatestFinishedActionsQuery(id).result)
      tags <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === process.id).result)
    } yield createFullDetails(
      process = process,
      processVersion = processVersion,
      lastActionData = actions.headOption,
      lastDeployedActionData = actions.headOption.find(_._1.isDeployed),
      isLatestVersion = isLatestVersion,
      tags = tags,
      history = processVersions.map(
        v => ProcessDBQueryRepository.toProcessVersion(v, actions.filter(p => p._1.processVersionId.contains(v.id)).toList)
      ),
    )
  }

  private def createFullDetails[PS: ProcessShapeFetchStrategy](process: ProcessEntityData,
                                                               processVersion: ProcessVersionEntityData,
                                                               lastActionData: Option[(ProcessActionEntityData, Option[CommentEntityData])],
                                                               lastDeployedActionData: Option[(ProcessActionEntityData, Option[CommentEntityData])],
                                                               isLatestVersion: Boolean,
                                                               tags: Seq[TagsEntityData] = List.empty,
                                                               history: Seq[ProcessVersion] = List.empty)(implicit loggedUser: LoggedUser): BaseProcessDetails[PS] = {
    BaseProcessDetails[PS](
      id = process.name.value, //TODO: replace by Long / ProcessId
      processId = process.id, //TODO: Remove it weh we will support Long / ProcessId
      name = process.name.value,
      processVersionId = processVersion.id,
      isLatestVersion = isLatestVersion,
      isArchived = process.isArchived,
      isSubprocess = process.isSubprocess,
      description = process.description,
      processingType = process.processingType,
      processCategory = process.processCategory,
      lastAction = lastActionData.map(ProcessDBQueryRepository.toProcessAction),
      lastDeployedAction = lastDeployedActionData.map(ProcessDBQueryRepository.toProcessAction),
      tags = tags.map(_.name).toList,
      modificationDate = processVersion.createDate.toInstant,
      modifiedAt = processVersion.createDate.toInstant,
      modifiedBy = processVersion.user,
      createdAt = process.createdAt.toInstant,
      createdBy = process.createdBy,
      json = convertToTargetShape(processVersion.json, process),
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  private def convertToTargetShape[PS: ProcessShapeFetchStrategy](maybeCanonical: Option[CanonicalProcess], process: ProcessEntityData): PS = {
    (maybeCanonical, implicitly[ProcessShapeFetchStrategy[PS]]) match {
      case (Some(canonical), ProcessShapeFetchStrategy.FetchCanonical) =>
        canonical.asInstanceOf[PS]
      case (Some(canonical), ProcessShapeFetchStrategy.FetchDisplayable) =>
        val displayableProcess = ProcessConverter.toDisplayableOrDie(canonical, process.processingType, process.processCategory)
        displayableProcess.asInstanceOf[PS]
      case (_, ProcessShapeFetchStrategy.NotFetch) => ().asInstanceOf[PS]
      case (None, strategy) => throw new IllegalArgumentException(s"Missing scenario json data, it's required to convert for strategy: $strategy.")
    }
  }
}
