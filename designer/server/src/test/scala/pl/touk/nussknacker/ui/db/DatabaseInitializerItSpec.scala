package pl.touk.nussknacker.ui.db

import com.dimafeng.testcontainers.ForAllTestContainer
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.{Ignore, OneInstancePerTest, OptionValues}
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, ScenarioComponentsUsages}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.initialization.Initialization
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessCreated, ProcessUpdated, UpdateProcessAction}

import scala.util.Using

//TODO just for test - I wil restore it soon
@Ignore
class DatabaseInitializerOnHsqlItSpec extends DatabaseInitializerItSpec with WithPostgresDbConfig

//TODO just for test - I wil restore it soon
@Ignore
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
    p1V1ComponentsUsages shouldBe ScenarioComponentsUsages(Map(
      ComponentIdParts(Some("barSource"), ComponentType.Source) -> List("source"),
      ComponentIdParts(Some("barService"), ComponentType.Processor) -> List("processor"),
      ComponentIdParts(Some("transformer"), ComponentType.CustomNode) -> List("custom"),
      ComponentIdParts(Some("barSink"), ComponentType.Sink) -> List("sink"),
    ))
  }

  private def initializeDbUpToMigrationVersion(targetVersion: String) = {
    val flywayUpToTestedMigration = DatabaseInitializer.configureFlyway("db", config)
      .target(targetVersion)
    flywayUpToTestedMigration.load().migrate()
  }

  private def saveSampleProcess(processName: String): ProcessCreated = {
    val action = CreateProcessAction(
      ProcessName(processName),
      TestCategories.TestCat,
      ProcessTestData.validProcessWithId(processName),
      TestProcessingTypes.Streaming,
      isFragment = false,
      forwardedUserName = None)
    val processCreated = dbioRunner.runInTransaction(writeRepository.saveNewProcess(action)).futureValue.rightValue.value
    clearComponentsUsages()
    processCreated
  }

  private def updateSampleProcess(processId: ProcessId, processName: String): ProcessUpdated = {
    val action = UpdateProcessAction(
      processId,
      ProcessTestData.validProcessWithId(processName),
      comment = None,
      increaseVersionWhenJsonNotChanged = true,
      forwardedUserName = None)
    val processUpdated = dbioRunner.runInTransaction(writeRepository.updateProcess(action)).futureValue.rightValue
    clearComponentsUsages()
    processUpdated
  }

  private def fetchComponentsUsages(p1Id: ProcessId, p1Version1Id: VersionId) = {
    repository.fetchProcessDetailsForId[ScenarioComponentsUsages](p1Id, p1Version1Id).futureValue.value.json
  }

  private def clearComponentsUsages(): Unit = {
    Using(db.db.createSession()) { session =>
      session.prepareStatement("""UPDATE "process_versions" SET "components_usages" = '[]'""").execute()
    }.get
  }

}
