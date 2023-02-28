package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.{ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentActions, ProcessActionEntityData, ProcessActionId}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.listener.Comment
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

//TODO: Add missing methods: markProcessAsDeployed and markProcessAsCancelled
trait ProcessActionRepository[F[_]] {
  def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[_]
  def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): F[_]
}

object DbProcessActionRepository {
  def create(dbConfig: DbConfig, modelData: ProcessingTypeDataProvider[ModelData])(implicit ec: ExecutionContext): DbProcessActionRepository =
    new DbProcessActionRepository(dbConfig, modelData.mapValues(_.configCreator.buildInfo()))
}

class DbProcessActionRepository(val dbConfig: DbConfig, buildInfos: ProcessingTypeDataProvider[Map[String, String]]) (implicit ec: ExecutionContext)
extends BasicRepository with EspTables with CommentActions with ProcessActionRepository[DB]{

  import profile.api._

  private val PrefixDeployedDeploymentComment = "Deployment: "
  private val PrefixCanceledDeploymentComment = "Stop: "

  //TODO: remove Deployment: after adding custom icons
  def markProcessAsDeployed(processId: ProcessId, processVersion: VersionId, processingType: ProcessingType, deploymentComment: Option[DeploymentComment])
                           (implicit user: LoggedUser): Future[ProcessActionEntityData] = {
    run(insertAction(
      processId,
      processVersion,
      deploymentComment.map(_.withPrefix(PrefixDeployedDeploymentComment)),
      ProcessActionType.Deploy,
      buildInfos.forType(processingType).map(BuildInfo.writeAsJson)))
  }

  //TODO: remove Stop: after adding custom icons
  def markProcessAsCancelled(processId: ProcessId, processVersion: VersionId, deploymentComment: Option[DeploymentComment])(implicit user: LoggedUser): Future[ProcessActionEntityData] =
    run(insertAction(processId, processVersion, deploymentComment.map(_.withPrefix(PrefixCanceledDeploymentComment)), ProcessActionType.Cancel, None))

  override def markProcessAsArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): DB[ProcessActionEntityData] =
    insertAction(processId, processVersion, None, ProcessActionType.Archive, None)

  override def markProcessAsUnArchived(processId: ProcessId, processVersion: VersionId)(implicit user: LoggedUser): DB[ProcessActionEntityData] =
    insertAction(processId, processVersion, None, ProcessActionType.UnArchive, None)

  private def insertAction(processId: ProcessId, processVersion: VersionId, comment: Option[Comment], action: ProcessActionType, buildInfo: Option[String])(implicit user: LoggedUser) = {
    val insertNew = processActionsTable.returning(processActionsTable.map(_.id)).into {
      case (entity, newId) => entity.copy(id = newId)
    }

    val now = Timestamp.from(Instant.now())
    for {
      commentId <- newCommentAction(processId, processVersion, comment)
      processActionData = ProcessActionEntityData(
        id = ProcessActionId(-1L),
        processId = processId,
        processVersionId = processVersion,
        user = user.username,
        createdAt = now,
        performedAt = now,
        action = action,
        state = ProcessActionState.Finished, // TODO: handle in progress actions
        commentId = commentId,
        buildInfo = buildInfo
      )
      result <- insertNew += processActionData
    } yield result
  }
}
