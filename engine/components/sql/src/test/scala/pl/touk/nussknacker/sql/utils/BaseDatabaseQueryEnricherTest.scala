package pl.touk.nussknacker.sql.utils

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import scala.concurrent.ExecutionContext

trait BaseDatabaseQueryEnricherTest extends FunSuite with Matchers with BeforeAndAfterAll with WithDB {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val contextId: ContextId = ContextId("")
  implicit val metaData: MetaData = MetaData("", StreamMetaData())
  implicit val collector: ServiceInvocationCollector = EmptyInvocationCollector.Instance

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
