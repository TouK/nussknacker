package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import cats.data.OptionT
import cats.implicits.toFunctorOps
import cats.instances.future._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.config.DesignerConfig.TechnicalUsers
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.process.{ScenarioQuery, repository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DBFetchingProcessRepository {

  def create(
      dbRef: DbRef,
      actionRepository: ScenarioActionReadOnlyRepository,
      scenarioLabelsRepository: ScenarioLabelsRepository
  )(implicit ec: ExecutionContext) =
    new DBFetchingProcessRepository[DB](
      dbRef,
      actionRepository,
      scenarioLabelsRepository
    ) with DbioRepository

  def createFutureRepository(
      dbRef: DbRef,
      actionReadOnlyRepository: ScenarioActionReadOnlyRepository,
      scenarioLabelsRepository: ScenarioLabelsRepository
  )(
      implicit ec: ExecutionContext
  ) =
    new DBFetchingProcessRepository[Future](
      dbRef,
      actionReadOnlyRepository,
      scenarioLabelsRepository
    ) with BasicRepository

}

// TODO: for the operations providing a single scenario details / id / processing type, we shouldn't pass LoggedUser
//       and do filtering on the DB side. Instead, we should return entity and check if user is authorized to access
//       to the resource on the services side
abstract class DBFetchingProcessRepository[F[_]: Monad](
    protected val dbRef: DbRef,
    actionRepository: ScenarioActionReadOnlyRepository,
    scenarioLabelsRepository: ScenarioLabelsRepository,
)(protected implicit val ec: ExecutionContext)
    extends FetchingProcessRepository[F]
    with LazyLogging {

  import api._

  override def getProcessVersion(
      processName: ProcessName,
      versionId: VersionId
  )(
      implicit user: LoggedUser,
  ): F[Option[ProcessVersion]] = {
    val result = for {
      processId <- OptionT(fetchProcessId(processName))
      details   <- OptionT(fetchProcessDetailsForId[CanonicalProcess](processId, versionId))
    } yield details.toEngineProcessVersion
    result.value
  }

  override def fetchLatestProcessesDetails[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery,
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[ScenarioWithDetailsEntity[PS]]] = {
    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      query.isFragment.map(arg => process => process.isFragment === arg),
      query.isArchived.map(arg => process => process.isArchived === arg),
      query.categories.map(arg => process => process.processCategory.inSet(arg)),
      query.processingTypes.map(arg => process => process.processingType.inSet(arg)),
      query.names.map(arg => process => process.name.inSet(arg)),
    )

    run(
      fetchLatestProcessDetailsByQueryAction(
        { process =>
          expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process))
        },
        query.isDeployed,
      )
    )
  }

  override def fetchLatestProcesses[PS: ScenarioShapeFetchStrategy](
      query: ScenarioQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[PS]] = {
    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      query.isFragment.map(arg => process => process.isFragment === arg),
      query.isArchived.map(arg => process => process.isArchived === arg),
      query.categories.map(arg => process => process.processCategory.inSet(arg)),
      query.processingTypes.map(arg => process => process.processingType.inSet(arg)),
      query.names.map(arg => process => process.name.inSet(arg)),
    )

    run(
      fetchLatestProcessByQueryAction(
        { process =>
          expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process))
        },
      )
    )
  }

  override def fetchLatestProcessVersionsCreatedByNonTechnicalUsers(
      query: ScenarioQuery,
      technicalUsers: TechnicalUsers,
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): F[Map[ProcessId, (VersionId, Timestamp, ProcessingType)]] = {
    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      query.isFragment.map(arg => process => process.isFragment === arg),
      query.isArchived.map(arg => process => process.isArchived === arg),
      query.categories.map(arg => process => process.processCategory.inSet(arg)),
      query.processingTypes.map(arg => process => process.processingType.inSet(arg)),
      query.names.map(arg => process => process.name.inSet(arg)),
    )

    run(
      fetchLatestProcessVersionsCreatedByNonTechnicalUsersQuery(
        process => expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process)),
        technicalUsers,
      ).result
    ).map(_.toMap)
  }

  private def fetchLatestProcessDetailsByQueryAction[PS: ScenarioShapeFetchStrategy](
      query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
      isDeployed: Option[Boolean],
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): DBIOAction[List[ScenarioWithDetailsEntity[PS]], NoStream, Effect.All with Effect.Read] = {
    (for {
      lastActionPerProcess <- fetchActionsOrEmpty(
        actionRepository.getLastActionPerProcess(ProcessActionState.FinishedStates, None)
      )
      lastStateActionPerProcess <- fetchActionsOrEmpty(
        actionRepository
          .getLastActionPerProcess(ProcessActionState.FinishedStates, Some(ScenarioActionName.StateActions))
      )
      // For last deploy action we are interested in Deploys that are Finished (not ExecutionFinished) and that are not Cancelled
      // so that the presence of such an action means that the process is currently deployed
      lastDeployedActionPerProcess <- fetchActionsOrEmpty(
        actionRepository
          .getLastActionPerProcess(
            ProcessActionState.FinishedStates,
            Some(Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel))
          )
      ).map(_.filter { case (_, action) =>
        action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.Finished
      })

      latestProcesses <- fetchLatestProcessesQuery(query, lastDeployedActionPerProcess.keySet, isDeployed).result
      labels          <- scenarioLabelsRepository.getLabels
    } yield latestProcesses
      .map { case ((_, processVersion), process) =>
        createFullDetails(
          process = process,
          processVersion = processVersion,
          latestProcessVersionByNonTechnicalUser = None,
          lastActionData = lastActionPerProcess.get(process.id),
          lastStateActionData = lastStateActionPerProcess.get(process.id),
          lastDeployedActionData = lastDeployedActionPerProcess.get(process.id),
          isLatestVersion = true,
          labels = labels.getOrElse(process.id, List.empty),
          // For optimisation reasons we don't return history when querying for list of processes
          history = None
        )
      }).map(_.toList)
  }

  private def fetchLatestProcessByQueryAction[PS: ScenarioShapeFetchStrategy](
      query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
  )(
      implicit loggedUser: LoggedUser,
  ): DBIOAction[List[PS], NoStream, Effect.All with Effect.Read] = {
    for {
      latestProcessEntities <- fetchLatestProcessesQuery(query).result
      latestProcesses = latestProcessEntities.map { case ((_, _), processVersion) =>
        convertToTargetShape(processVersion)
      }.toList
    } yield latestProcesses
  }

  private def fetchActionsOrEmpty[PS: ScenarioShapeFetchStrategy](
      doFetch: => DBIO[Map[ProcessId, ProcessAction]]
  ): DBIO[Map[ProcessId, ProcessAction]] = {
    implicitly[ScenarioShapeFetchStrategy[PS]] match {
      // For component usages we don't need full process details, so we don't fetch actions
      case ScenarioShapeFetchStrategy.FetchComponentsUsages => DBIO.successful(Map.empty)
      case _                                                => doFetch
    }
  }

  override def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId,
      technicalUsersOpt: Option[TechnicalUsers],
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ScenarioWithDetailsEntity[PS]]] = {
    run(fetchLatestProcessDetailsForProcessIdQuery(id, technicalUsersOpt))
  }

  override def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](
      processId: ProcessId,
      versionId: VersionId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[ScenarioWithDetailsEntity[PS]]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(processId)(ScenarioShapeFetchStrategy.NotFetch).result.headOption
      )
      processVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(processId).filter(pv => pv.id === versionId).result.headOption
      )
      processDetails <- fetchProcessDetailsForVersion(
        processVersion,
        isLatestVersion = latestProcessVersion.id == processVersion.id,
        technicalUsersOpt = None,
      )
    } yield processDetails
    run(action.value)
  }

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): F[Option[ProcessId]] = {
    run(processesTable.filter(_.name === processName).map(_.id).result.headOption.map(_.map(id => id)))
  }

  def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): F[Option[ProcessName]] = {
    run(processesTable.filter(_.id === processId).map(_.name).result.headOption)
  }

  override def fetchProcessingType(
      processId: ProcessIdWithName
  )(implicit user: LoggedUser, ec: ExecutionContext): F[ProcessingType] = {
    run {
      implicit val fetchStrategy: ScenarioShapeFetchStrategy[_] = ScenarioShapeFetchStrategy.NotFetch
      fetchLatestProcessDetailsForProcessIdQuery(processId.id, None).flatMap {
        case None          => DBIO.failed(ProcessNotFoundError(processId.name))
        case Some(process) => DBIO.successful(process.processingType)
      }
    }
  }

  private def fetchLatestProcessDetailsForProcessIdQuery[PS: ScenarioShapeFetchStrategy](
      id: ProcessId,
      technicalUsers: Option[TechnicalUsers],
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): DB[Option[ScenarioWithDetailsEntity[PS]]] = {
    (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(id).result.headOption
      )
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true, technicalUsers)
    } yield processDetails).value
  }

  private def fetchProcessDetailsForVersion[PS: ScenarioShapeFetchStrategy](
      processVersion: ProcessVersionEntityData,
      isLatestVersion: Boolean,
      technicalUsersOpt: Option[TechnicalUsers],
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): OptionT[DB, ScenarioWithDetailsEntity[PS]] = {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](
        fetchProcessLatestVersionsQuery(id)(ScenarioShapeFetchStrategy.NotFetch).result
      )
      actions <- OptionT.liftF[DB, List[ProcessAction]](actionRepository.getFinishedProcessActions(id, None))
      labels  <- OptionT.liftF(scenarioLabelsRepository.getLabels(id))
    } yield createFullDetails(
      process = process,
      processVersion = processVersion,
      latestProcessVersionByNonTechnicalUser = technicalUsersOpt
        .flatMap { technicalUsers =>
          processVersions
            .filter(v => !technicalUsers.userNames.contains(v.user))
            .maxByOption(_.id.value)
        },
      lastActionData = actions.headOption,
      lastStateActionData = actions.find(a => ScenarioActionName.StateActions.contains(a.actionName)),
      // For last deploy action we are interested in Deploys that are Finished (not ExecutionFinished) and that are not Cancelled
      // so that the presence of such an action means that the process is currently deployed
      lastDeployedActionData = actions
        .find(action => Set(ScenarioActionName.Deploy, ScenarioActionName.Cancel).contains(action.actionName))
        .filter(action =>
          action.actionName == ScenarioActionName.Deploy && action.state == ProcessActionState.Finished
        ),
      isLatestVersion = isLatestVersion,
      labels = labels,
      history = Some(
        processVersions.map(v =>
          ProcessDBQueryRepository.toProcessVersion(v, actions.filter(p => p.processVersionId == v.id))
        )
      ),
    )
  }

  private def createFullDetails[PS: ScenarioShapeFetchStrategy](
      process: ProcessEntityData,
      processVersion: ProcessVersionEntityData,
      latestProcessVersionByNonTechnicalUser: Option[ProcessVersionEntityData],
      lastActionData: Option[ProcessAction],
      lastStateActionData: Option[ProcessAction],
      lastDeployedActionData: Option[ProcessAction],
      isLatestVersion: Boolean,
      labels: List[ScenarioLabel],
      history: Option[Seq[ScenarioVersion]]
  ): ScenarioWithDetailsEntity[PS] = {
    repository.ScenarioWithDetailsEntity[PS](
      processId = process.id,
      name = process.name,
      processVersionId = processVersion.id,
      isLatestVersion = isLatestVersion,
      isArchived = process.isArchived,
      isFragment = process.isFragment,
      description = process.description,
      processingType = process.processingType,
      processCategory = process.processCategory,
      lastAction = lastActionData,
      lastStateAction = lastStateActionData,
      lastDeployedAction = lastDeployedActionData,
      scenarioLabels = labels.map(_.value),
      modificationDate = processVersion.createDate.toInstant,
      modifiedAt = processVersion.createDate.toInstant,
      modifiedBy = processVersion.user,
      modifiedByNonTechnicalUserAt = latestProcessVersionByNonTechnicalUser.map(_.createDate.toInstant),
      modifiedByNonTechnicalUser = latestProcessVersionByNonTechnicalUser.map(_.user),
      createdAt = process.createdAt.toInstant,
      createdBy = process.createdBy,
      json = convertToTargetShape(processVersion),
      history = history.map(_.toList),
      modelVersion = processVersion.modelVersion
    )
  }

  private def convertToTargetShape[PS: ScenarioShapeFetchStrategy](
      processVersion: ProcessVersionEntityData
  ): PS = {
    (processVersion.json, processVersion.componentsUsages, implicitly[ScenarioShapeFetchStrategy[PS]]) match {
      case (Some(canonical), _, ScenarioShapeFetchStrategy.FetchCanonical) =>
        canonical
      case (Some(canonical), _, ScenarioShapeFetchStrategy.FetchScenarioGraph) =>
        val scenarioGraph =
          CanonicalProcessConverter.toScenarioGraph(canonical)
        scenarioGraph
      case (_, _, ScenarioShapeFetchStrategy.NotFetch) => ()
      case (_, Some(componentsUsages), ScenarioShapeFetchStrategy.FetchComponentsUsages) =>
        componentsUsages
      case (_, _, strategy) =>
        throw new IllegalArgumentException(
          s"Missing scenario json data, it's required to convert for strategy: $strategy."
        )
    }
  }

}
