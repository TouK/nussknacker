package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.{ProcessAction, ProcessVersion, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait ProcessRepository[F[_]] extends Repository[F] with EspTables {
  import api._

  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntityFactory#ProcessEntity, ProcessEntityData, Seq] = {
    loggedUser match {
      case user: CommonUser => processesTable.filter(_.processCategory inSet user.categories(Permission.Read))
      case _: AdminUser => processesTable
    }
  }

  protected def fetchProcessLatestVersionsQuery(processId: ProcessId)(implicit fetchShape: ProcessShapeFetchStrategy[_]): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processVersionsTableQuery
      .filter(_.processId === processId.value)
      .sortBy(_.createDate.desc)

  protected def fetchLastDeployedActionPerProcessQuery: Query[(api.Rep[Long], (ProcessActionEntityFactory#ProcessActionEntity, api.Rep[Option[CommentEntityFactory#CommentEntity]])), (Long, (ProcessActionEntityData, Option[CommentEntityData])), Seq] =
    fetchLastActionPerProcessQuery.filter(_._2._1.action === ProcessActionType.Deploy)

  protected def fetchLastActionPerProcessQuery: Query[(Rep[Long], ProcessDeploymentInfoEntityFactory#ProcessDeploymentInfoEntity), (Long, DeployedProcessInfoEntityData), Seq] =
    processActionsTable
      .groupBy(_.processId)
      .map { case (processId, group) => (processId, group.map(_.deployedAt).max) }
      .join(processActionsTable)
      .on { case ((processId, latestDeployedAt), deployAction) => deployAction.processId === processId && deployAction.deployedAt === latestDeployedAt } //We fetch exactly this one  with max deployment
      .map { case ((processId, _), deployAction) => processId -> deployAction }
      .joinLeft(commentsTable)
      .on { case ((_, action), comment) => action.commentId === comment.id }
      .map{ case ((processId, action), comment) => processId -> (action, comment) }

  protected def fetchProcessLatestActionsQuery(processId: ProcessId): Query[(ProcessActionEntityFactory#ProcessActionEntity, Rep[Option[CommentEntityFactory#CommentEntity]]), (ProcessActionEntityData, Option[CommentEntityData]), Seq] =
    processActionsTable
      .filter(_.processId === processId.value)
      .sortBy(_.deployedAt.desc)
      .joinLeft(commentsTable)
      .on { case (action, comment) => action.commentId === comment.id }
      .map{ case (action, comment) => (action, comment) }

  protected def fetchLatestProcessesQuery(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
                                          lastDeployedActionPerProcess: Seq[(Long, (ProcessActionEntityData, Option[CommentEntityData]))],
                                          isDeployed: Option[Boolean])(implicit fetchShape: ProcessShapeFetchStrategy[_], loggedUser: LoggedUser, ec: ExecutionContext): Query[(((Rep[Long], Rep[Option[Timestamp]]), ProcessVersionEntityFactory#BaseProcessVersionEntity), ProcessEntityFactory#ProcessEntity), (((Long, Option[Timestamp]), ProcessVersionEntityData), ProcessEntityData), Seq] =
    processVersionsTableNoJson
      .groupBy(_.processId)
      .map { case (n, group) => (n, group.map(_.createDate).max) }
      .join(processVersionsTableQuery)
      .on { case (((processId, latestVersionDate)), processVersion) => processVersion.processId === processId && processVersion.createDate === latestVersionDate }
      .join(processTableFilteredByUser.filter(query))
      .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
      .filter{ case ((_, _), process) =>
        isDeployed match {
          case None => true: Rep[Boolean]
          case Some(dep) => process.id.inSet(lastDeployedActionPerProcess.map(_._1)) === dep
        }
      }

  protected def fetchTagsPerProcess(implicit fetchShape: ProcessShapeFetchStrategy[_], ec: ExecutionContext): DBIOAction[Map[Long, List[TagsEntityData]], NoStream, Effect.Read] =
    tagsTable.result.map(_.toList.groupBy(_.processId).withDefaultValue(Nil))

  protected def processVersionsTableQuery(implicit fetchShape: ProcessShapeFetchStrategy[_]): TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity] =
    fetchShape match {
      case ProcessShapeFetchStrategy.FetchDisplayable | ProcessShapeFetchStrategy.FetchCanonical =>
        processVersionsTable.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
      case ProcessShapeFetchStrategy.NotFetch =>
        processVersionsTableNoJson.asInstanceOf[TableQuery[ProcessVersionEntityFactory#BaseProcessVersionEntity]]
    }

  protected def latestProcessVersionsNoJsonQuery(processName: ProcessName): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processesTable
      .filter(_.name === processName.value)
      .join(processVersionsTableNoJson)
      .on { case (process, version) => process.id === version.id }
      .map(_._2)
      .sortBy(_.createDate.desc)
}

object ProcessRepository {

  def toProcessVersion(versionEntity: ProcessVersionEntityData): ProcessVersion = ProcessVersion(
    processVersionId = versionEntity.id,
    createDate = DateUtils.toLocalDateTime(versionEntity.createDate),
    modelVersion = versionEntity.modelVersion,
    user = versionEntity.user
  )

  def toProcessAction(actionData: (ProcessActionEntityData, Option[CommentEntityData])): ProcessAction = ProcessAction(
    processVersionId = actionData._1.processVersionId,
    createdAt = actionData._1.deployedAtTime,
    user = actionData._1.user,
    action = actionData._1.action,
    commentId = actionData._2.map(_.id),
    comment = actionData._2.map((_.content)),
    buildInfo = actionData._1.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
  )

  case class ProcessNotFoundError(id: String) extends Exception(s"No process $id found") with NotFoundError

  case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Process $id already exists"
  }

  case class ProcessAlreadyDeployed(id: String) extends BadRequestError {
    def getMessage = s"Process $id is already deployed"
  }

  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }
}
