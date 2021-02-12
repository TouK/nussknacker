package pl.touk.nussknacker.ui.initialization

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.migrate.TestMigrations
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

import scala.concurrent.ExecutionContextExecutor

@Slow
class InitializationOnPostgresItSpec
  extends FlatSpec
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

  private def sampleDeploymentData(processId: String) = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(
    ProcessTestData.validProcessWithId(processId))).noSpaces)

  it should "add technical processes" in {
    Initialization.init(migrations, db, "env1", customProcesses)

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.processType)) shouldBe List(("process1", ProcessType.Custom))
  }

  it should "migrate processes" in {
    saveSampleProcess()

    Initialization.init(migrations, db, "env1", None)

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when subprocesses present" in {
    saveSampleProcess("sub1", subprocess = true)
    saveSampleProcess("id1")

    Initialization.init(migrations, db, "env1", None)

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("id1", Some(2)))
  }

  private def saveSampleProcess(processName: String = processId, subprocess: Boolean = false): Unit = {
    val action = CreateProcessAction(ProcessName(processName), "RTM", sampleDeploymentData(processId), TestProcessingTypes.Streaming, subprocess)

    repositoryManager
      .run(writeRepository.saveNewProcess(action))
      .futureValue
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(1, 2, 5)), db, "env1", customProcesses))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private val customProcesses =
    Some(Map("process1" -> "pl.touk.nussknacker.CustomProcess"))

}
