package pl.touk.nussknacker.ui.initialization

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.tags.Slow
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.migrate.TestMigrations
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

import scala.concurrent.ExecutionContextExecutor

@Slow
class InitializationOnPostgresItSpec
  extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithPostgresDbTesting {

  import Initialization.nussknackerUser

  private implicit val ds: ExecutionContextExecutor = system.dispatcher

  private val processId = "proc1"

  private val migrations = mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newFetchingProcessRepository(db, Some(1))

  private lazy val repositoryManager = TestFactory.newDBRepositoryManager(db)

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(db)

  private def sampleDeploymentData(processId: String) =
    ProcessTestData.validProcessWithId(processId).toCanonicalProcess

  it should "migrate processes" in {
    saveSampleProcess()

    Initialization.init(migrations, db, "env1")

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when fragments present" in {
    saveSampleProcess("sub1", subprocess = true)
    saveSampleProcess("id1")

    Initialization.init(migrations, db, "env1")

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("id1", Some(2)))
  }

  private def saveSampleProcess(processName: String = processId, subprocess: Boolean = false): Unit = {
    val action = CreateProcessAction(ProcessName(processName), "RTM", sampleDeploymentData(processId), TestProcessingTypes.Streaming, subprocess)

    repositoryManager
      .runInTransaction(writeRepository.saveNewProcess(action))
      .futureValue
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(1, 2, 5)), db, "env1"))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

}
