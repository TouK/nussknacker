package pl.touk.nussknacker.ui.initialization

import java.io.File
import java.nio.file.Files

import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.PrettyParams
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory, WithDbTesting}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.migrate.TestMigrations
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class InitializationItSpec extends FlatSpec with ScalatestRouteTest with Matchers with ScalaFutures with BeforeAndAfterEach with WithDbTesting with Eventually {

  import Initialization.toukUser

  private val processId = "proc1"

  private var processesDir: File = _
  private val migrations = Map(ProcessingType.Streaming -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newProcessRepository(db, Some(1))

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(db)

  private def sampleDeploymentData(processId: String) = GraphProcess(UiProcessMarshaller.toJson(ProcessCanonizer.canonize(
    ProcessTestData.validProcessWithId(processId)), PrettyParams.nospace))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    processesDir = Files.createTempDirectory("processesJsons").toFile
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    processesDir.delete()
  }

  it should "add technical processes" in {

    prepareCustomProcessFile()

    Initialization.init(migrations, db, "env1", processesDir)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.processType)) shouldBe List(("process1", ProcessType.Custom))
  }

  it should "migrate processes" in {

    saveSampleProcess()

    Initialization.init(migrations, db, "env1", processesDir)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when subprocesses present" in {
    (1 to 20).foreach { id =>
      saveSampleProcess(s"sub$id", subprocess = true)
    }

    (1 to 20).foreach { id =>
      saveSampleProcess(s"id$id")
    }

    Initialization.init(migrations, db, "env1", processesDir)

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)).toSet shouldBe (1 to 20).map(id => (s"id$id", Some(2))).toSet

  }

  private def saveSampleProcess(processId: String = processId, subprocess: Boolean = false) : Unit = {
    writeRepository.saveNewProcess(processId, "RTM", sampleDeploymentData(processId), ProcessingType.Streaming, subprocess).futureValue
  }

  it should "run initialization transactionally" in {
    prepareCustomProcessFile()
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(Map(ProcessingType.Streaming -> new TestMigrations(1, 2, 5)), db, "env1", processesDir))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails().futureValue.map(d => (d.id, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private def prepareCustomProcessFile() = {
    FileUtils.write(new File(processesDir, TechnicalProcessUpdate.customProcessFile), """{ process1: "pl.touk.nussknacker.CustomProcess"}""")
  }
}
