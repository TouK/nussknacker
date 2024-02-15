package pl.touk.nussknacker.ui.initialization

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.test.base.db.{DbTesting, WithHsqlDbTesting, WithPostgresDbTesting, WithTestDb}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.migrate.TestMigrations
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction

class InitializationOnHsqlItSpec extends InitializationOnDbItSpec with WithHsqlDbTesting

@Slow
class InitializationOnPostgresItSpec extends InitializationOnDbItSpec with WithPostgresDbTesting

abstract class InitializationOnDbItSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll {
  this: DbTesting with WithTestDb =>

  import Initialization.nussknackerUser

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processName = ProcessName("proc1")

  private val migrations = mapProcessingTypeDataProvider("streaming" -> new TestMigrations(1, 2))

  private lazy val repository = TestFactory.newFetchingProcessRepository(testDbRef)

  private lazy val dbioRunner = TestFactory.newDBIOActionRunner(testDbRef)

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(testDbRef)

  private def sampleCanonicalProcess(processName: ProcessName) = ProcessTestData.validProcessWithName(processName)

  it should "migrate processes" in {
    saveSampleProcess()

    Initialization.init(migrations, testDbRef, repository, "env1")

    dbioRunner
      .runInTransaction(
        repository.fetchLatestProcessesDetails[Unit](ScenarioQuery.unarchivedProcesses)
      )
      .futureValue
      .map(d => (d.name.value, d.modelVersion)) shouldBe List(("proc1", Some(2)))
  }

  it should "migrate processes when fragments present" in {
    (1 to 20).foreach { id =>
      saveSampleProcess(ProcessName(s"sub$id"), fragment = true)
    }

    (1 to 20).foreach { id =>
      saveSampleProcess(ProcessName(s"id$id"))
    }

    Initialization.init(migrations, testDbRef, repository, "env1")

    dbioRunner
      .runInTransaction(
        repository.fetchLatestProcessesDetails[Unit](ScenarioQuery.unarchivedProcesses)
      )
      .futureValue
      .map(d => (d.name.value, d.modelVersion))
      .toSet shouldBe (1 to 20).map(id => (s"id$id", Some(2))).toSet
  }

  it should "run initialization transactionally" in {
    saveSampleProcess()

    val exception = intercept[RuntimeException](
      Initialization.init(
        mapProcessingTypeDataProvider("streaming" -> new TestMigrations(1, 2, 5)),
        testDbRef,
        repository,
        "env1"
      )
    )

    exception.getMessage shouldBe "made to fail.."

    dbioRunner
      .runInTransaction(
        repository.fetchLatestProcessesDetails[Unit](ScenarioQuery.unarchivedProcesses)
      )
      .futureValue
      .map(d => (d.name.value, d.modelVersion)) shouldBe List(("proc1", Some(1)))
  }

  private def saveSampleProcess(processName: ProcessName = processName, fragment: Boolean = false): Unit = {
    val action = CreateProcessAction(
      processName = processName,
      category = "RTM",
      canonicalProcess = sampleCanonicalProcess(processName),
      processingType = "streaming",
      isFragment = fragment,
      forwardedUserName = None
    )

    dbioRunner
      .runInTransaction(writeRepository.saveNewProcess(action))
      .futureValue
  }

}
