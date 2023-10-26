package pl.touk.nussknacker.ui.process.repository

import cats.Monad
import cats.data.OptionT
import cats.instances.future._
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity._
import pl.touk.nussknacker.ui.listener.services.{RepositoryScenarioWithDetails, ScenarioShapeFetchStrategy}
import pl.touk.nussknacker.ui.process.ProcessesQuery
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessNotFoundError
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DBFetchingProcessRepository {

  def create(dbRef: DbRef, actionRepository: ProcessActionRepository[DB])(implicit ec: ExecutionContext) =
    new DBFetchingProcessRepository[DB](dbRef, actionRepository) with DbioRepository

  def createFutureRepository(dbRef: DbRef, actionRepository: ProcessActionRepository[DB])(
      implicit ec: ExecutionContext
  ) =
    new DBFetchingProcessRepository[Future](dbRef, actionRepository) with BasicRepository

}

abstract class DBFetchingProcessRepository[F[_]: Monad](val dbRef: DbRef, actionRepository: ProcessActionRepository[DB])
    extends FetchingProcessRepository[F]
    with LazyLogging {

  import api._

  override def fetchProcessesDetails[PS: ScenarioShapeFetchStrategy](
      query: ProcessesQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[List[RepositoryScenarioWithDetails[PS]]] = {
    val expr: List[Option[ProcessEntityFactory#ProcessEntity => Rep[Boolean]]] = List(
      query.isFragment.map(arg => process => process.isFragment === arg),
      query.isArchived.map(arg => process => process.isArchived === arg),
      query.categories.map(arg => process => process.processCategory.inSet(arg)),
      query.processingTypes.map(arg => process => process.processingType.inSet(arg)),
      query.names.map(arg => process => process.name.inSet(arg)),
    )

    run(
      fetchProcessDetailsByQueryAction(
        { process =>
          expr.flatten.foldLeft(true: Rep[Boolean])((x, y) => x && y(process))
        },
        query.isDeployed
      )
    )
  }

  private def fetchProcessDetailsByQueryAction[PS: ScenarioShapeFetchStrategy](
      query: ProcessEntityFactory#ProcessEntity => Rep[Boolean],
      isDeployed: Option[Boolean]
  )(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): DBIOAction[List[RepositoryScenarioWithDetails[PS]], NoStream, Effect.All with Effect.Read] = {
    (for {
      lastActionPerProcess <- fetchActionsOrEmpty(
        actionRepository.getLastActionPerProcess(ProcessActionState.FinishedStates, None)
      )
      lastStateActionPerProcess <- fetchActionsOrEmpty(
        actionRepository
          .getLastActionPerProcess(ProcessActionState.FinishedStates, Some(ProcessActionType.StateActionsTypes))
      )
      // for last deploy action we are not interested in ExecutionFinished deploys - we don't want to show them in the history
      lastDeployedActionPerProcess <- fetchActionsOrEmpty(
        actionRepository.getLastActionPerProcess(Set(ProcessActionState.Finished), Some(Set(ProcessActionType.Deploy)))
      )
      latestProcesses <- fetchLatestProcessesQuery(query, lastDeployedActionPerProcess.keySet, isDeployed).result
    } yield latestProcesses
      .map { case ((_, processVersion), process) =>
        createFullDetails(
          process,
          processVersion,
          lastActionPerProcess.get(process.id),
          lastStateActionPerProcess.get(process.id),
          lastDeployedActionPerProcess.get(process.id),
          isLatestVersion = true
        )
      }).map(_.toList)
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
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[RepositoryScenarioWithDetails[PS]]] = {
    run(fetchLatestProcessDetailsForProcessIdQuery(id))
  }

  override def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](
      processId: ProcessId,
      versionId: VersionId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): F[Option[RepositoryScenarioWithDetails[PS]]] = {
    val action = for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(processId)(ScenarioShapeFetchStrategy.NotFetch).result.headOption
      )
      processVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(processId).filter(pv => pv.id === versionId).result.headOption
      )
      processDetails <- fetchProcessDetailsForVersion(
        processVersion,
        isLatestVersion = latestProcessVersion.id == processVersion.id
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

  override def fetchProcessDetails(
      processName: ProcessName
  )(implicit ec: ExecutionContext): F[Option[ProcessEntityData]] = {
    run(processesTable.filter(_.name === processName).result.headOption)
  }

  override def fetchProcessingType(
      processId: ProcessId
  )(implicit user: LoggedUser, ec: ExecutionContext): F[ProcessingType] = {
    run {
      implicit val fetchStrategy: ScenarioShapeFetchStrategy[_] = ScenarioShapeFetchStrategy.NotFetch
      fetchLatestProcessDetailsForProcessIdQuery(processId).flatMap {
        case None          => DBIO.failed(ProcessNotFoundError(processId.value.toString))
        case Some(process) => DBIO.successful(process.processingType)
      }
    }
  }

  private def fetchLatestProcessDetailsForProcessIdQuery[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): DB[Option[RepositoryScenarioWithDetails[PS]]] = {
    (for {
      latestProcessVersion <- OptionT[DB, ProcessVersionEntityData](
        fetchProcessLatestVersionsQuery(id).result.headOption
      )
      processDetails <- fetchProcessDetailsForVersion(latestProcessVersion, isLatestVersion = true)
    } yield processDetails).value
  }

  private def fetchProcessDetailsForVersion[PS: ScenarioShapeFetchStrategy](
      processVersion: ProcessVersionEntityData,
      isLatestVersion: Boolean
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): OptionT[DB, RepositoryScenarioWithDetails[PS]] = {
    val id = processVersion.processId
    for {
      process <- OptionT[DB, ProcessEntityData](processTableFilteredByUser.filter(_.id === id).result.headOption)
      processVersions <- OptionT.liftF[DB, Seq[ProcessVersionEntityData]](
        fetchProcessLatestVersionsQuery(id)(ScenarioShapeFetchStrategy.NotFetch).result
      )
      actions <- OptionT.liftF[DB, List[ProcessAction]](actionRepository.getFinishedProcessActions(id, None))
      tags    <- OptionT.liftF[DB, Seq[TagsEntityData]](tagsTable.filter(_.processId === process.id).result)
    } yield createFullDetails(
      process = process,
      processVersion = processVersion,
      lastActionData = actions.headOption,
      lastStateActionData = actions.find(a => ProcessActionType.StateActionsTypes.contains(a.actionType)),
      // for last deploy action we are not interested in ExecutionFinished deploys - we don't want to show them in the history
      lastDeployedActionData = actions.headOption.filter(a =>
        a.actionType == ProcessActionType.Deploy && a.state == ProcessActionState.Finished
      ),
      isLatestVersion = isLatestVersion,
      tags = tags,
      history = processVersions.map(v =>
        ProcessDBQueryRepository.toProcessVersion(v, actions.filter(p => p.processVersionId == v.id))
      ),
    )
  }

  private def createFullDetails[PS: ScenarioShapeFetchStrategy](
      process: ProcessEntityData,
      processVersion: ProcessVersionEntityData,
      lastActionData: Option[ProcessAction],
      lastStateActionData: Option[ProcessAction],
      lastDeployedActionData: Option[ProcessAction],
      isLatestVersion: Boolean,
      tags: Seq[TagsEntityData] = List.empty,
      history: Seq[ProcessVersion] = List.empty
  ): RepositoryScenarioWithDetails[PS] = {
    RepositoryScenarioWithDetails[PS](
      id = process.name.value, // TODO: replace by Long / ProcessId
      processId = process.id,  // TODO: Remove it weh we will support Long / ProcessId
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
      tags = tags.map(_.name).toList,
      modificationDate = processVersion.createDate.toInstant,
      modifiedAt = processVersion.createDate.toInstant,
      modifiedBy = processVersion.user,
      createdAt = process.createdAt.toInstant,
      createdBy = process.createdBy,
      json = convertToTargetShape(processVersion, process),
      history = history.toList,
      modelVersion = processVersion.modelVersion
    )
  }

  private def convertToTargetShape[PS: ScenarioShapeFetchStrategy](
      processVersion: ProcessVersionEntityData,
      process: ProcessEntityData
  ): PS = {
    (processVersion.json, processVersion.componentsUsages, implicitly[ScenarioShapeFetchStrategy[PS]]) match {
      case (Some(canonical), _, ScenarioShapeFetchStrategy.FetchCanonical) =>
        canonical.asInstanceOf[PS]
      case (Some(canonical), _, ScenarioShapeFetchStrategy.FetchDisplayable) =>
        val displayableProcess =
          ProcessConverter.toDisplayableOrDie(canonical, process.processingType, process.processCategory)
        displayableProcess.asInstanceOf[PS]
      case (_, _, ScenarioShapeFetchStrategy.NotFetch) => ().asInstanceOf[PS]
      case (_, Some(componentsUsages), ScenarioShapeFetchStrategy.FetchComponentsUsages) =>
        componentsUsages.asInstanceOf[PS]
      case (_, _, strategy) =>
        throw new IllegalArgumentException(
          s"Missing scenario json data, it's required to convert for strategy: $strategy."
        )
    }
  }

}
