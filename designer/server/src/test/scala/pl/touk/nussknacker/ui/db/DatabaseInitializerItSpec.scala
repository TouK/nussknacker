package pl.touk.nussknacker.ui.db

import com.dimafeng.testcontainers.ForAllTestContainer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OneInstancePerTest, OptionValues}
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.component.{ComponentUsages, ScenarioComponentsUsages}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessCreated, ProcessUpdated, UpdateProcessAction}

class DatabaseInitializerOnHsqlItSpec extends DatabaseInitializerItSpec with WithPostgresDbConfig

@Slow
class DatabaseInitializerOnPostgresItSpec extends DatabaseInitializerItSpec with WithPostgresDbConfig

abstract class DatabaseInitializerItSpec extends AnyFlatSpec
  with ForAllTestContainer
  with OneInstancePerTest
  with Matchers
  with PatientScalaFutures
  with OptionValues
  with EitherValuesDetailedMessage
  with WithDbConfig {

  import Initialization.nussknackerUser

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val repository = TestFactory.newFutureFetchingProcessRepository(db)

  private lazy val dbioRunner = TestFactory.newDBIOActionRunner(db)

  private lazy val writeRepository = TestFactory.newWriteProcessRepository(db)

  it should "execute fill components migration" in {
    initializeDbUpToMigrationVersion("1.038")
    val ProcessCreated(p1Id, p1Version1Id) = saveSampleProcess("proc1")
    val p1Version2Id = updateSampleProcess(p1Id, "proc1").newVersion.value
    val ProcessCreated(p2Id, p2Version1Id) = saveSampleProcess("proc2")

    DatabaseInitializer.initDatabase("db", config)

    val p1V1ComponentsUsages = fetchComponentsUsages(p1Id, p1Version1Id)
    val p1V2ComponentsUsages = fetchComponentsUsages(p1Id, p1Version2Id)
    val p2V1ComponentsUsages = fetchComponentsUsages(p2Id, p2Version1Id)
    Set(p1V1ComponentsUsages, p1V2ComponentsUsages, p2V1ComponentsUsages) should have size 1
    p1V1ComponentsUsages.value.toSet shouldBe Set(
      ComponentUsages(Some("barSource"), ComponentType.Source, List("source")),
      ComponentUsages(Some("barService"), ComponentType.Processor, List("processor")),
      ComponentUsages(Some("transformer"), ComponentType.CustomNode, List("custom")),
      ComponentUsages(Some("barSink"), ComponentType.Sink, List("sink")),
    )
  }

  private def initializeDbUpToMigrationVersion(targetVersion: String) = {
    val flywayUpToTestedMigration = DatabaseInitializer.configureFlyway("db", config)
      .target(targetVersion)
    flywayUpToTestedMigration.load().migrate()
  }

  private def saveSampleProcess(processName: String): ProcessCreated = {
    val action = CreateProcessAction(ProcessName(processName), TestCategories.TestCat, ProcessTestData.validProcessWithId(processName), TestProcessingTypes.Streaming, isSubprocess = false, ScenarioComponentsUsages.Empty)
    dbioRunner.runInTransaction(writeRepository.saveNewProcess(action)).futureValue.rightValue.value
  }

  private def updateSampleProcess(processId: ProcessId, processName: String): ProcessUpdated = {
    val action = UpdateProcessAction(processId, ProcessTestData.validProcessWithId(processName), ScenarioComponentsUsages.Empty, comment = None, increaseVersionWhenJsonNotChanged = true)
    dbioRunner.runInTransaction(writeRepository.updateProcess(action)).futureValue.rightValue
  }

  private def fetchComponentsUsages(p1Id: ProcessId, p1Version1Id: VersionId) = {
    repository.fetchProcessDetailsForId[ScenarioComponentsUsages](p1Id, p1Version1Id).futureValue.value.json
  }

}
