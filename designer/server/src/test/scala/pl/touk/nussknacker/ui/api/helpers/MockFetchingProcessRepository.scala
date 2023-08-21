package pl.touk.nussknacker.ui.api.helpers

import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.processdetails
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy.{FetchCanonical, FetchComponentsUsages, FetchDisplayable, NotFetch}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.{BasicRepository, FetchingProcessRepository, ScenarioComponentsUsagesHelper}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object MockFetchingProcessRepository {

  def withProcessesDetails(processes: List[ProcessDetails])(implicit ec: ExecutionContext): MockFetchingProcessRepository = {
    val canonicals = processes.map { p => p.mapProcess(ProcessConverter.fromDisplayable) }
    new MockFetchingProcessRepository(canonicals)
  }

}

class MockFetchingProcessRepository(processes: List[BaseProcessDetails[CanonicalProcess]])(implicit ec: ExecutionContext) extends FetchingProcessRepository[Future] with BasicRepository {

  //It's only for BasicRepository implementation, we don't use it
  private val config: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)
  val dbRef: DbRef = DbRef(JdbcBackend.Database.forConfig("db", config), HsqldbProfile)

  override def fetchProcessesDetails[PS: ProcessShapeFetchStrategy](q: FetchProcessesDetailsQuery)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(
      p => check(q.isFragment, p.isFragment) && check(q.isArchived, p.isArchived) && check(q.isDeployed, p.lastStateAction.exists(_.actionType.equals(ProcessActionType.Deploy))) && checkSeq(q.categories, p.processCategory) && checkSeq(q.processingTypes, p.processingType)
    ))

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(p => p.idWithName.id == id).lastOption)

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.find(p => p.idWithName.id == processId && p.processVersionId == versionId))

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]] =
    Future(processes.find(p => p.idWithName.name == processName).map(_.processId))

  override def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]] =
    Future(processes.find(p => p.processId == processId).map(_.idWithName.name))

  override def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[String] =
    getUserProcesses[Unit].map(_.find(p => p.processId == processId).map(_.processingType).get)

  //TODO: Implement
  override def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessEntityData]] = ???

  private def getUserProcesses[PS: ProcessShapeFetchStrategy](implicit loggedUser: LoggedUser) = getProcesses[PS].map(_.filter(p =>
    loggedUser.isAdmin || loggedUser.can(p.processCategory, Permission.Read)
  ))

  private def getProcesses[PS: ProcessShapeFetchStrategy]: Future[List[BaseProcessDetails[PS]]] = {
    val shapeStrategy: ProcessShapeFetchStrategy[PS] = implicitly[ProcessShapeFetchStrategy[PS]]
    Future(processes.map(p => convertProcess(p)(shapeStrategy)))
  }

  private def convertProcess[PS: ProcessShapeFetchStrategy](process: BaseProcessDetails[CanonicalProcess]): BaseProcessDetails[PS] = {
    val shapeStrategy: ProcessShapeFetchStrategy[PS] = implicitly[ProcessShapeFetchStrategy[PS]]

    shapeStrategy match {
      case NotFetch => process.copy(json = ().asInstanceOf[PS])
      case FetchCanonical => process.asInstanceOf[BaseProcessDetails[PS]]
      case FetchDisplayable => process.mapProcess(canonical => ProcessConverter.toDisplayableOrDie(canonical, process.processingType, process.processCategory)).asInstanceOf[BaseProcessDetails[PS]]
      case FetchComponentsUsages => process.mapProcess(canonical => ScenarioComponentsUsagesHelper.compute(canonical)).asInstanceOf[BaseProcessDetails[PS]]
    }
  }

  private def check[T](condition: Option[T], value: T) = condition.forall(_ == value)

  private def checkSeq[T](condition: Option[Seq[T]], value: T) = condition.forall(_.contains(value))
}
