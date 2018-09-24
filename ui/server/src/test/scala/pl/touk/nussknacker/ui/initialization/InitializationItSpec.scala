package pl.touk.nussknacker.ui.initialization

import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.PrettyParams
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithDbTesting}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessType
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.migrate.TestMigrations

class InitializationItSpec extends FlatSpec with ScalatestRouteTest with Matchers with ScalaFutures with BeforeAndAfterEach with WithDbTesting with Eventually {

  import Initialization.toukUser

  private val processId = "proc1"

  private val migrations = Map(TestProcessingTypes.Streaming -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newProcessRepository(db, Some(1))

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(db)

  private def sampleDeploymentData(processId: String) = GraphProcess(UiProcessMarshaller.toJson(ProcessCanonizer.canonize(
    ProcessTestData.validProcessWithId(processId)), PrettyParams.nospace))

  it should "add technical processes" in {

    Initialization.init(migrations, db, "env1", customProcesses)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.processType)) shouldBe List(("process1", ProcessType.Custom))
  }

  it should "migrate processes" in {

    saveSampleProcess()

    Initialization.init(migrations, db, "env1", None)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when subprocesses present" in {
    (1 to 20).foreach { id =>
      saveSampleProcess(s"sub$id", subprocess = true)
    }

    (1 to 20).foreach { id =>
      saveSampleProcess(s"id$id")
    }

    Initialization.init(migrations, db, "env1", None)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)).toSet shouldBe (1 to 20).map(id => (s"id$id", Some(2))).toSet

  }

  private def saveSampleProcess(processId: String = processId, subprocess: Boolean = false) : Unit = {
    writeRepository.saveNewProcess(processId, "RTM", sampleDeploymentData(processId), TestProcessingTypes.Streaming, subprocess).futureValue
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(Map(TestProcessingTypes.Streaming -> new TestMigrations(1, 2, 5)), db, "env1", customProcesses))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private val customProcesses =
    Some(Map("process1" -> "pl.touk.nussknacker.CustomProcess"))

}
