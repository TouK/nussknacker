package pl.touk.nussknacker.ui.initialization

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.migrate.TestMigrations
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

class InitializationOnHsqlItSpec extends InitializationOnDbItSpec with WithHsqlDbTesting

@Slow
class InitializationOnPostgresItSpec extends InitializationOnDbItSpec with WithPostgresDbTesting

abstract class InitializationOnDbItSpec
  extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with DbTesting {

  import Initialization.nussknackerUser

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processId = "proc1"

  private val migrations = mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newFutureFetchingProcessRepository(db)

  private lazy val dbioRunner = TestFactory.newDBIOActionRunner(db)

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(db)

  private def sampleCanonicalProcess(processId: String) = ProcessTestData.validProcessWithId(processId)

  it should "migrate processes" in {
    saveSampleProcess()

    Initialization.init(migrations, db, "env1")

    repository.fetchProcessesDetails[Unit](FetchProcessesDetailsQuery.unarchivedProcesses).futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when fragments present" in {
    (1 to 20).foreach { id =>
      saveSampleProcess(s"sub$id", subprocess = true)
    }

    (1 to 20).foreach { id =>
      saveSampleProcess(s"id$id")
    }

    Initialization.init(migrations, db, "env1")

    repository.fetchProcessesDetails[Unit](FetchProcessesDetailsQuery.unarchivedProcesses).futureValue.map(d => (d.name, d.modelVersion)).toSet shouldBe (1 to 20).map(id => (s"id$id", Some(2))).toSet
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> new TestMigrations(1, 2, 5)), db, "env1"))

    exception.getMessage shouldBe "made to fail.."

    repository.fetchProcessesDetails[Unit](FetchProcessesDetailsQuery.unarchivedProcesses).futureValue.map(d => (d.name, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private def saveSampleProcess(processName: String = processId, subprocess: Boolean = false): Unit = {
    val action = CreateProcessAction(
      ProcessName(processName),
      "RTM",
      sampleCanonicalProcess(processId),
      TestProcessingTypes.Streaming,
      subprocess,
      forwardedUserName = None)

    dbioRunner
      .runInTransaction(writeRepository.saveNewProcess(action))
      .futureValue
  }

}
