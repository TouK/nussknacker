package pl.touk.nussknacker.ui.api.helpers

import cats.instances.future._
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.listener.services.ScenarioShapeFetchStrategy.{
  FetchCanonical,
  FetchComponentsUsages,
  FetchDisplayable,
  NotFetch
}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.listener.services.{RepositoryScenarioWithDetails, ScenarioShapeFetchStrategy}
import pl.touk.nussknacker.ui.process.ProcessesQuery
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.{
  BasicRepository,
  FetchingProcessRepository,
  ScenarioComponentsUsagesHelper
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object MockFetchingProcessRepository {

  def withProcessesDetails(
      processes: List[RepositoryScenarioWithDetails[DisplayableProcess]]
  )(implicit ec: ExecutionContext): MockFetchingProcessRepository = {
    val canonicals = processes.map { p => p.mapScenario(ProcessConverter.fromDisplayable) }

    new MockFetchingProcessRepository(
      TestFactory.dummyDbRef, // It's only for BasicRepository implementation, we don't use it
      canonicals
    )
  }

}

class MockFetchingProcessRepository private (
    override val dbRef: DbRef,
    processes: List[RepositoryScenarioWithDetails[CanonicalProcess]]
)(implicit ec: ExecutionContext)
    extends FetchingProcessRepository[Future]
    with BasicRepository {

  override def fetchProcessesDetails[PS: ScenarioShapeFetchStrategy](
      q: ProcessesQuery
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[RepositoryScenarioWithDetails[PS]]] =
    getUserProcesses[PS].map(
      _.filter(p =>
        check(q.isFragment, p.isFragment) && check(q.isArchived, p.isArchived) && check(
          q.isDeployed,
          p.lastStateAction.exists(_.actionType.equals(ProcessActionType.Deploy))
        ) && checkSeq(q.categories, p.processCategory) && checkSeq(q.processingTypes, p.processingType)
      )
    )

  override def fetchLatestProcessDetailsForProcessId[PS: ScenarioShapeFetchStrategy](
      id: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[RepositoryScenarioWithDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(p => p.idWithName.id == id).lastOption)

  override def fetchProcessDetailsForId[PS: ScenarioShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(
      implicit loggedUser: LoggedUser,
      ec: ExecutionContext
  ): Future[Option[RepositoryScenarioWithDetails[PS]]] =
    getUserProcesses[PS].map(_.find(p => p.idWithName.id == processId && p.processVersionId == versionId))

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]] =
    Future(processes.find(p => p.idWithName.name == processName).map(_.processId))

  override def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]] =
    Future(processes.find(p => p.processId == processId).map(_.idWithName.name))

  override def fetchProcessingType(
      processId: ProcessId
  )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[String] =
    getUserProcesses[Unit].map(_.find(p => p.processId == processId).map(_.processingType).get)

  // TODO: Implement
  override def fetchProcessDetails(processName: ProcessName)(
      implicit ec: ExecutionContext
  ): Future[Option[ProcessEntityData]] = ???

  private def getUserProcesses[PS: ScenarioShapeFetchStrategy](implicit loggedUser: LoggedUser) =
    getProcesses[PS].map(_.filter(p => loggedUser.isAdmin || loggedUser.can(p.processCategory, Permission.Read)))

  private def getProcesses[PS: ScenarioShapeFetchStrategy]: Future[List[RepositoryScenarioWithDetails[PS]]] = {
    val shapeStrategy: ScenarioShapeFetchStrategy[PS] = implicitly[ScenarioShapeFetchStrategy[PS]]
    Future(processes.map(p => convertProcess(p)(shapeStrategy)))
  }

  private def convertProcess[PS: ScenarioShapeFetchStrategy](
      process: RepositoryScenarioWithDetails[CanonicalProcess]
  ): RepositoryScenarioWithDetails[PS] = {
    val shapeStrategy: ScenarioShapeFetchStrategy[PS] = implicitly[ScenarioShapeFetchStrategy[PS]]

    shapeStrategy match {
      case NotFetch       => process.copy(json = ().asInstanceOf[PS])
      case FetchCanonical => process.asInstanceOf[RepositoryScenarioWithDetails[PS]]
      case FetchDisplayable =>
        process
          .mapScenario(canonical =>
            ProcessConverter.toDisplayableOrDie(canonical, process.processingType, process.processCategory)
          )
          .asInstanceOf[RepositoryScenarioWithDetails[PS]]
      case FetchComponentsUsages =>
        process
          .mapScenario(canonical => ScenarioComponentsUsagesHelper.compute(canonical))
          .asInstanceOf[RepositoryScenarioWithDetails[PS]]
    }
  }

  private def check[T](condition: Option[T], value: T) = condition.forall(_ == value)

  private def checkSeq[T](condition: Option[Seq[T]], value: T) = condition.forall(_.contains(value))
}
