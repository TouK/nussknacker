package pl.touk.nussknacker.ui.process.repository

import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.Comment
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessAlreadyExists
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{
  CreateProcessAction,
  ProcessUpdated,
  UpdateProcessAction
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class DBFetchingProcessRepositorySpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with PatientScalaFutures
    with TestPermissions {

  import cats.syntax.either._

  private val dbioRunner = DBIOActionRunner(testDbRef)

  private val writingRepo =
    new DBProcessRepository(testDbRef, mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> 0)) {
      override protected def now: Instant = currentTime
    }

  private var currentTime: Instant = Instant.now()

  private val actions =
    DbProcessActionRepository.create(testDbRef, MapBasedProcessingTypeDataProvider.withEmptyCombinedData(Map.empty))

  private val fetching = DBFetchingProcessRepository.createFutureRepository(testDbRef, actions)

  private val activities = DbProcessActivityRepository(testDbRef)

  private implicit val user: LoggedUser = TestFactory.adminUser()

  test("fetch processes for category") {

    def saveProcessForCategory(cat: String) = {
      saveProcess(
        ScenarioBuilder
          .streaming(s"categorized-$cat")
          .source("s", "")
          .emptySink("sink", ""),
        Instant.now(),
        category = cat
      )
    }

    val c1Reader = TestFactory.user(permissions = "c1" -> Permission.Read)

    saveProcessForCategory("c1")
    saveProcessForCategory("c2")
    val processes = fetching
      .fetchProcessesDetails(FetchProcessesDetailsQuery(isArchived = Some(false)))(
        ProcessShapeFetchStrategy.NotFetch,
        c1Reader,
        implicitly[ExecutionContext]
      )
      .futureValue

    processes.map(_.name) shouldEqual "categorized-c1" :: Nil
  }

  test("should rename process") {
    val oldName  = ProcessName("oldName")
    val oldName2 = ProcessName("oldName2")
    val newName  = ProcessName("newName")

    saveProcess(
      ScenarioBuilder
        .streaming(oldName.value)
        .source("s", "")
        .emptySink("s2", ""),
      Instant.now()
    )
    saveProcess(
      ScenarioBuilder
        .streaming(oldName2.value)
        .source("s", "")
        .emptySink("s2", ""),
      Instant.now()
    )

    processExists(oldName) shouldBe true
    processExists(oldName2) shouldBe true
    processExists(newName) shouldBe false

    val before = fetchMetaDataIdsForAllVersions(oldName)
    before.toSet shouldBe Set(oldName.value)

    renameProcess(oldName, newName)

    processExists(oldName) shouldBe false
    processExists(oldName2) shouldBe true
    processExists(newName) shouldBe true

    val oldAfter = fetchMetaDataIdsForAllVersions(oldName)
    val newAfter = fetchMetaDataIdsForAllVersions(newName)
    oldAfter.length shouldBe 0
    newAfter.length shouldBe before.length
    newAfter.toSet shouldBe Set(newName.value)
  }

  // TODO: remove this in favour of process-audit-log
  test("should add comment when renamed") {
    val oldName = ProcessName("oldName")
    val newName = ProcessName("newName")

    saveProcess(
      ScenarioBuilder
        .streaming(oldName.value)
        .source("s", "")
        .emptySink("s2", ""),
      Instant.now()
    )
    processExists(newName) shouldBe false

    renameProcess(oldName, newName)

    val comments = fetching
      .fetchProcessId(newName)
      .flatMap(v => activities.findActivity(ProcessIdWithName(v.get, newName)).map(_.comments))
      .futureValue

    atLeast(1, comments) should matchPattern {
      case Comment(_, "newName", VersionId(1L), "Rename: [oldName] -> [newName]", user.username, _) =>
    }
  }

  test("should prevent rename to existing name") {
    val oldName      = ProcessName("oldName")
    val existingName = ProcessName("existingName")

    saveProcess(
      ScenarioBuilder
        .streaming(oldName.value)
        .source("s", "")
        .emptySink("s2", ""),
      Instant.now()
    )
    saveProcess(
      ScenarioBuilder
        .streaming(existingName.value)
        .source("s", "")
        .emptySink("s2", ""),
      Instant.now()
    )

    processExists(oldName) shouldBe true
    processExists(existingName) shouldBe true

    (the[TestFailedException] thrownBy {
      renameProcess(oldName, existingName)
    }).cause.value shouldBe ProcessAlreadyExists(existingName.value)
  }

  test("should generate new process version id based on latest version id") {

    val processName     = ProcessName("processName")
    val latestVersionId = VersionId(4)
    val now             = Instant.now()
    val espProcess = ScenarioBuilder
      .streaming(processName.value)
      .source("s", "")
      .emptySink("s2", "")

    saveProcess(espProcess, now)

    val details: BaseProcessDetails[CanonicalProcess] = fetchLatestProcessDetails(processName)
    details.processVersionId shouldBe VersionId.initialVersionId

    // change of id for version imitates situation where versionId is different from number of all process versions (ex. after manual JSON removal from DB)
    dbioRunner.runInTransaction(
      writingRepo.changeVersionId(details.processId, details.processVersionId, latestVersionId)
    )

    val latestDetails = fetchLatestProcessDetails[CanonicalProcess](processName)
    latestDetails.processVersionId shouldBe latestVersionId

    val ProcessUpdated(_, oldVersionInfoOpt, newVersionInfoOpt) =
      updateProcess(latestDetails.processId, ProcessTestData.validProcess)
    oldVersionInfoOpt shouldBe Symbol("defined")
    oldVersionInfoOpt.get shouldBe latestVersionId
    newVersionInfoOpt shouldBe Symbol("defined")
    newVersionInfoOpt.get shouldBe latestVersionId.increase

  }

  test("should generate new process version id on increaseVersionWhenJsonNotChanged action param") {

    val processName = ProcessName("processName")
    val now         = Instant.now()
    val espProcess = ScenarioBuilder
      .streaming(processName.value)
      .source("s", "")
      .emptySink("s2", "")

    saveProcess(espProcess, now)

    val latestDetails = fetchLatestProcessDetails[CanonicalProcess](processName)
    latestDetails.processVersionId shouldBe VersionId.initialVersionId

    updateProcess(
      latestDetails.processId,
      ProcessTestData.validProcess,
      increaseVersionWhenJsonNotChanged = false
    ).newVersion.get shouldBe VersionId(2)
    // without force
    updateProcess(
      latestDetails.processId,
      ProcessTestData.validProcess,
      increaseVersionWhenJsonNotChanged = false
    ).newVersion shouldBe empty
    // now with force
    updateProcess(
      latestDetails.processId,
      ProcessTestData.validProcess,
      increaseVersionWhenJsonNotChanged = true
    ).newVersion.get shouldBe VersionId(3)

  }

  test("should store components usages") {
    val processName = ProcessName("proc1")
    val newScenario = ScenarioBuilder
      .streaming(processName.value)
      .source("source1", "source")
      .emptySink("sink1", "sink")

    saveProcess(newScenario)

    val latestDetails = fetchLatestProcessDetails[ScenarioComponentsUsages](processName)
    latestDetails.json shouldBe ScenarioComponentsUsages(
      Map(
        ComponentIdParts(Some("source"), ComponentType.Source) -> List("source1"),
        ComponentIdParts(Some("sink"), ComponentType.Sink)     -> List("sink1"),
      )
    )

    val updatedScenario = ScenarioBuilder
      .streaming(processName.value)
      .source("source1", "source")
      .emptySink("sink1", "otherSink")

    updateProcess(latestDetails.processId, updatedScenario)

    fetchLatestProcessDetails[ScenarioComponentsUsages](processName).json shouldBe ScenarioComponentsUsages(
      Map(
        ComponentIdParts(Some("source"), ComponentType.Source)  -> List("source1"),
        ComponentIdParts(Some("otherSink"), ComponentType.Sink) -> List("sink1"),
      )
    )
  }

  private def processExists(processName: ProcessName): Boolean =
    fetching.fetchProcessId(processName).futureValue.nonEmpty

  private def updateProcess(
      processId: ProcessId,
      canonicalProcess: CanonicalProcess,
      increaseVersionWhenJsonNotChanged: Boolean = false
  ): ProcessUpdated = {
    val action = UpdateProcessAction(
      processId,
      canonicalProcess,
      comment = None,
      increaseVersionWhenJsonNotChanged,
      forwardedUserName = None
    )

    dbioRunner.runInTransaction(writingRepo.updateProcess(action)).futureValue
  }

  private def saveProcess(espProcess: CanonicalProcess, now: Instant = Instant.now(), category: String = "") = {
    currentTime = now
    val action = CreateProcessAction(
      ProcessName(espProcess.id),
      category,
      espProcess,
      TestProcessingTypes.Streaming,
      isFragment = false,
      forwardedUserName = None
    )

    dbioRunner.runInTransaction(writingRepo.saveNewProcess(action)).futureValue
  }

  private def renameProcess(processName: ProcessName, newName: ProcessName) = {
    val processId = fetching.fetchProcessId(processName).futureValue.get
    dbioRunner
      .runInTransaction(writingRepo.renameProcess(ProcessIdWithName(processId, processName), newName))
      .futureValue
  }

  private def fetchMetaDataIdsForAllVersions(name: ProcessName) = {
    fetching.fetchProcessId(name).futureValue.toSeq.flatMap { processId =>
      fetching
        .fetchProcessesDetails[DisplayableProcess](FetchProcessesDetailsQuery.unarchived)
        .futureValue
        .filter(_.processId.value == processId.value)
        .map(_.json)
        .map(_.metaData.id)
    }
  }

  private def fetchLatestProcessDetails[PS: ProcessShapeFetchStrategy](
      name: ProcessName
  ): processdetails.BaseProcessDetails[PS] = {
    val fetchedProcess = fetching
      .fetchProcessId(name)
      .futureValue
      .flatMap(
        fetching.fetchLatestProcessDetailsForProcessId(_).futureValue
      )

    fetchedProcess shouldBe Symbol("defined")
    fetchedProcess.get
  }

}
