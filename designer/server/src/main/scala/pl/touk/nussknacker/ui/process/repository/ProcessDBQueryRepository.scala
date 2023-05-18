package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessShapeFetchStrategy, ProcessVersion}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

//FIXME: It's temporary trait. In future we should merge and refactor: DBFetchingProcessRepository, ProcessDBQueryRepository and DBProcessRepository to one repository
trait ProcessDBQueryRepository[F[_]] extends Repository[F] with EspTables {
  import api._

  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntityFactory#ProcessEntity, ProcessEntityData, Seq] = {
    loggedUser match {
      case user: CommonUser => processesTable.filter(_.processCategory inSet user.categories(Permission.Read))
      case _: AdminUser => processesTable
    }
  }

  protected def fetchProcessLatestVersionsQuery(processId: ProcessId)(implicit fetchShape: ProcessShapeFetchStrategy[_]): Query[ProcessVersionEntityFactory#BaseProcessVersionEntity, ProcessVersionEntityData, Seq] =
    processVersionsTableQuery
      .filter(_.processId === processId)
      .sortBy(_.id.desc)

  protected def fetchLastDeployedActionPerProcessQuery: Query[(api.Rep[ProcessId], (ProcessActionEntityFactory#ProcessActionEntity, api.Rep[Option[CommentEntityFactory#CommentEntity]])), (ProcessId, (ProcessActionEntityData, Option[CommentEntityData])), Seq] =
    fetchLastFinishedActionPerProcessQuery
      .filter(_._2._1.action === ProcessActionType.Deploy)

  protected def fetchLastFinishedActionPerProcessQuery: Query[(Rep[ProcessId], (ProcessActionEntityFactory#ProcessActionEntity, Rep[Option[CommentEntityFactory#CommentEntity]])), (ProcessId, (ProcessActionEntityData, Option[CommentEntityData])), Seq] =
    processActionsTable
      .filter(_.state === ProcessActionState.Finished)
      .groupBy(_.processId)
      .map { case (processId, group) => (processId, group.map(_.performedAt).max) }
      .join(processActionsTable)
      .on { case ((processId, maxPerformedAt), action) => action.processId === processId && action.state === ProcessActionState.Finished && action.performedAt === maxPerformedAt } //We fetch exactly this one  with max deployment
      .map { case ((processId, _), action) => processId -> action }
      .joinLeft(commentsTable)
      .on { case ((_, action), comment) => action.commentId === comment.id }
      .map{ case ((processId, action), comment) => processId -> (action, comment) }

  protected def fetchProcessLatestFinishedActionsQuery(processId: ProcessId): Query[(ProcessActionEntityFactory#ProcessActionEntity, Rep[Option[CommentEntityFactory#CommentEntity]]), (ProcessActionEntityData, Option[CommentEntityData]), Seq] =
    processActionsTable
      .filter(p => p.processId === processId && p.state === ProcessActionState.Finished)
      .joinLeft(commentsTable)
      .on { case (action, comment) => action.commentId === comment.id }
      .map{ case (action, comment) => (action, comment) }
      .sortBy(_._1.performedAt.desc)

  protected def fetchLatestProcessesQuery(query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
                                          lastDeployedActionPerProcess: Seq[(ProcessId, (ProcessActionEntityData, Option[CommentEntityData]))],
                                          isDeployed: Option[Boolean])(implicit fetchShape: ProcessShapeFetchStrategy[_], loggedUser: LoggedUser, ec: ExecutionContext): Query[(((Rep[ProcessId], Rep[Option[Timestamp]]), ProcessVersionEntityFactory#BaseProcessVersionEntity), ProcessEntityFactory#ProcessEntity), (((ProcessId, Option[Timestamp]), ProcessVersionEntityData), ProcessEntityData), Seq] =
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

  protected def fetchTagsPerProcess(implicit fetchShape: ProcessShapeFetchStrategy[_], ec: ExecutionContext): DBIOAction[Map[ProcessId, List[TagsEntityData]], NoStream, Effect.Read] =
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
      .filter(_.name === processName)
      .join(processVersionsTableNoJson)
      .on { case (process, version) => process.id === version.processId }
      .map(_._2)
      .sortBy(_.createDate.desc)
}

object ProcessDBQueryRepository {

  def toProcessVersion(versionData: ProcessVersionEntityData, actions: List[(ProcessActionEntityData, Option[CommentEntityData])]): ProcessVersion = ProcessVersion(
    processVersionId = versionData.id,
    createDate = versionData.createDate.toInstant,
    modelVersion = versionData.modelVersion,
    user = versionData.user,
    actions = actions.map(toProcessAction)
  )

  def toProcessAction(actionData: (ProcessActionEntityData, Option[CommentEntityData])): ProcessAction = ProcessAction(
    processVersionId = actionData._1.processVersionId.getOrElse(throw new AssertionError(s"Process version not available for finished action: ${actionData._1}")),
    performedAt = actionData._1.performedAtTime.getOrElse(throw new AssertionError(s"PerformedAt not available for finished action: ${actionData._1}")),
    user = actionData._1.user,
    action = actionData._1.action,
    commentId = actionData._2.map(_.id),
    comment = actionData._2.map((_.content)),
    buildInfo = actionData._1.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty)
  )

  case class ProcessNotFoundError(id: String) extends Exception(s"No scenario $id found") with NotFoundError

  case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Scenario $id already exists"
  }

  case class ProcessAlreadyDeployed(id: String) extends BadRequestError {
    def getMessage = s"Scenario $id is already deployed"
  }

  case class InvalidProcessJson(rawJson: String) extends BadRequestError {
    def getMessage = s"Invalid raw json string: $rawJson"
  }
}
