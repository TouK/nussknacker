package pl.touk.nussknacker.ui.process.repository

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.Comment
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessAlreadyExists
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessUpdated, UpdateProcessAction}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class DBFetchingProcessRepositorySpec
  extends AnyFunSuite
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with PatientScalaFutures
    with TestPermissions {
  import cats.syntax.either._

  private val repositoryManager = RepositoryManager.createDbRepositoryManager(db)

  private val writingRepo = new DBProcessRepository(db, mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> 0)) {
    override protected def now: Instant = currentTime
  }

  private var currentTime : Instant = Instant.now()

  private val fetching = DBFetchingProcessRepository.create(db)

  private val activities = DbProcessActivityRepository(db)

  private implicit val user: LoggedUser = TestFactory.adminUser()

  test("fetch processes for category") {

    def saveProcessForCategory(cat :String) = {
      saveProcess(ScenarioBuilder
        .streaming(s"categorized-$cat")
        .source("s", "")
        .emptySink("sink", ""),
        Instant.now(),
        category = cat
      )
    }
    val c1Reader = TestFactory.user(permissions = "c1"->Permission.Read)

    saveProcessForCategory("c1")
    saveProcessForCategory("c2")
    val processes= fetching.fetchProcessesDetails(FetchProcessesDetailsQuery(isArchived = Some(false)))(ProcessShapeFetchStrategy.NotFetch, c1Reader, implicitly[ExecutionContext]).futureValue

    processes.map(_.name) shouldEqual "categorized-c1"::Nil
  }

  test("should rename process") {
    val oldName = ProcessName("oldName")
    val oldName2 = ProcessName("oldName2")
    val newName = ProcessName("newName")

    saveProcess(ScenarioBuilder
      .streaming(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .source("s", "")
      .emptySink("s2", ""),
      Instant.now()
    )
    saveProcess(ScenarioBuilder
      .streaming(oldName2.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .source("s", "")
      .emptySink("s2", ""),
      Instant.now()
    )

    processExists(oldName) shouldBe true
    processExists(oldName2) shouldBe true
    processExists(newName) shouldBe false

    val before = fetchMetaDataIdsForAllVersions(oldName)
    before.toSet shouldBe Set(oldName.value)

    renameProcess(oldName, newName) shouldBe 'right

    processExists(oldName) shouldBe false
    processExists(oldName2) shouldBe true
    processExists(newName) shouldBe true

    val oldAfter = fetchMetaDataIdsForAllVersions(oldName)
    val newAfter = fetchMetaDataIdsForAllVersions(newName)
    oldAfter.length shouldBe 0
    newAfter.length shouldBe before.length
    newAfter.toSet shouldBe Set(newName.value)
  }

  // todo: remove this in favour of process-audit-log
  test("should add comment when renamed") {
    val oldName = ProcessName("oldName")
    val newName = ProcessName("newName")

    saveProcess(ScenarioBuilder
      .streaming(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .source("s", "")
      .emptySink("s2", ""),
      Instant.now()
    )
    processExists(newName) shouldBe false

    renameProcess(oldName, newName) shouldBe 'right

    val comments = fetching.fetchProcessId(newName)
      .flatMap(v => activities.findActivity(ProcessIdWithName(v.get, newName)).map(_.comments))
      .futureValue

    atLeast(1, comments) should matchPattern {
      case Comment(_, "newName", VersionId(1L), "Rename: [oldName] -> [newName]", user.username, _) =>
    }
  }

  test("should prevent rename to existing name") {
    val oldName = ProcessName("oldName")
    val existingName = ProcessName("existingName")

    saveProcess(ScenarioBuilder
      .streaming(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .source("s", "")
      .emptySink("s2", ""),
      Instant.now()
    )
    saveProcess(ScenarioBuilder
      .streaming(existingName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .source("s", "")
      .emptySink("s2", ""),
      Instant.now()
    )

    processExists(oldName) shouldBe true
    processExists(existingName) shouldBe true

    renameProcess(oldName, existingName) shouldBe ProcessAlreadyExists(existingName.value).asLeft
  }

  test("should generate new process version id based on latest version id") {

    val processName = ProcessName("processName")
    val latestVersionId = VersionId(4)
    val now = Instant.now()
    val espProcess = ScenarioBuilder
      .streaming(processName.value)
      .source("s", "")
      .emptySink("s2", "")

    saveProcess(espProcess, now)

    val details: BaseProcessDetails[CanonicalProcess] = fetchLatestProcessDetails(processName)
    details.processVersionId shouldBe VersionId.initialVersionId

    //change of id for version imitates situation where versionId is different from number of all process versions (ex. after manual JSON removal from DB)
    repositoryManager.runInTransaction(
      writingRepo.changeVersionId(details.processId, details.processVersionId, latestVersionId)
    )

    val latestDetails = fetchLatestProcessDetails(processName)
    latestDetails.processVersionId shouldBe latestVersionId

    val ProcessUpdated(processId, oldVersionInfoOpt, newVersionInfoOpt) = updateProcess(latestDetails.processId, ProcessTestData.validProcess, false)
    oldVersionInfoOpt shouldBe 'defined
    oldVersionInfoOpt.get shouldBe latestVersionId
    newVersionInfoOpt shouldBe 'defined
    newVersionInfoOpt.get shouldBe latestVersionId.increase

  }

  test("should generate new process version id on increaseVersionWhenJsonNotChanged action param") {

    val processName = ProcessName("processName")
    val now = Instant.now()
    val espProcess = ScenarioBuilder.streaming(processName.value)
      .source("s", "")
      .emptySink("s2", "")

    saveProcess(espProcess, now)

    val latestDetails = fetchLatestProcessDetails(processName)
    latestDetails.processVersionId shouldBe VersionId.initialVersionId

    updateProcess(latestDetails.processId, ProcessTestData.validProcess, false).newVersion.get shouldBe VersionId(2)
    //without force
    updateProcess(latestDetails.processId, ProcessTestData.validProcess, false).newVersion shouldBe empty
    //now with force
    updateProcess(latestDetails.processId, ProcessTestData.validProcess, true).newVersion.get shouldBe VersionId(3)

  }

  private def processExists(processName: ProcessName): Boolean =
    fetching.fetchProcessId(processName).futureValue.nonEmpty

  private def updateProcess(processId: ProcessId, canonicalProcess: CanonicalProcess, increaseVersionWhenJsonNotChanged: Boolean): ProcessUpdated = {
    val action = UpdateProcessAction(processId, canonicalProcess, UpdateProcessComment(""), increaseVersionWhenJsonNotChanged)

    val processUpdated = repositoryManager.runInTransaction(writingRepo.updateProcess(action)).futureValue
    processUpdated shouldBe 'right
    processUpdated.right.get
  }

  private def saveProcess(espProcess: CanonicalProcess, now: Instant, category: String = "") = {
    currentTime = now
    val action = CreateProcessAction(ProcessName(espProcess.id), category, espProcess, TestProcessingTypes.Streaming, false)

    repositoryManager.runInTransaction(writingRepo.saveNewProcess(action)).futureValue shouldBe 'right
  }

  private def renameProcess(processName: ProcessName, newName: ProcessName) = {
    val processId = fetching.fetchProcessId(processName).futureValue.get
    repositoryManager.runInTransaction(writingRepo.renameProcess(ProcessIdWithName(processId, processName), newName)).futureValue
  }

  private def fetchMetaDataIdsForAllVersions(name: ProcessName) = {
    fetching.fetchProcessId(name).futureValue.toSeq.flatMap { processId =>
      fetching.fetchAllProcessesDetails[DisplayableProcess]().futureValue
        .filter(_.processId.value == processId.value)
        .map(_.json)
        .map(_.metaData.id)
    }
  }

  private def fetchLatestProcessDetails(name: ProcessName): processdetails.BaseProcessDetails[CanonicalProcess] = {
    val fetchedProcess = fetching.fetchProcessId(name).futureValue.flatMap(
      fetching.fetchLatestProcessDetailsForProcessId[CanonicalProcess](_).futureValue
    )

    fetchedProcess shouldBe 'defined
    fetchedProcess.get
  }
}
