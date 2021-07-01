package pl.touk.nussknacker.sql.utils

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{ContextId, JobData, Lifecycle, MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, ToCollect}
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.QueryServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future}

trait BaseDatabaseQueryEnricherTest extends FunSuite with Matchers with BeforeAndAfterAll with WithDB {

  import cats.implicits.catsStdInstancesForFuture

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val contextId: ContextId = ContextId("")
  implicit val metaData: MetaData = MetaData("", StreamMetaData())
  implicit val collector: ServiceInvocationCollector = new ServiceInvocationCollector {
    override def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])
                                       (action: => Future[InvocationCollectors.CollectableAction[A]], names: InvocationCollectors.TransmissionNames)
                                       (implicit ec: ExecutionContext): Future[A] = new QueryServiceInvocationCollector()
      .collectWithResponse(contextId, NodeId("id"), "id", request, mockValue, action, names)
  }

  val jobData: JobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty, DeploymentData.empty)

  val service: Lifecycle

  override def beforeAll(): Unit = {
    service.open(jobData)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    service.close()
    super.afterAll()
  }
}
