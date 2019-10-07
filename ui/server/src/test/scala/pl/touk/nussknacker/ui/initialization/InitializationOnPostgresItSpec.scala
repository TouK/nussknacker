package pl.touk.nussknacker.ui.initialization

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.process.migrate.TestMigrations

@Slow
class InitializationOnPostgresItSpec
  extends FlatSpec
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithPostgresDbTesting
    with Eventually {

  import Initialization.nussknackerUser

  private val processId = "proc1"

  private val migrations = Map(TestProcessingTypes.Streaming -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newProcessRepository(db, Some(1))

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
    writeRepository.saveNewProcess(ProcessName(processName), "RTM", sampleDeploymentData(processId), TestProcessingTypes.Streaming, subprocess).futureValue
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(Map(TestProcessingTypes.Streaming -> new TestMigrations(1, 2, 5)), db, "env1", customProcesses))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails[Unit]().futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private val customProcesses =
    Some(Map("process1" -> "pl.touk.nussknacker.CustomProcess"))

}
