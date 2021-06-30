package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestPermissions, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.Comment
import pl.touk.nussknacker.ui.process.repository.ProcessDBQueryRepository.ProcessAlreadyExists
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{CreateProcessAction, ProcessUpdated, UpdateProcessAction}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext

class DBFetchingProcessRepositorySpec
  extends FunSuite
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with PatientScalaFutures
    with TestPermissions {
  import cats.syntax.either._

  private val repositoryManager = RepositoryManager.createDbRepositoryManager(db)

  private val writingRepo = new DBProcessRepository(db, mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> 0)) {
    override protected def now: LocalDateTime = currentTime
  }
  private var currentTime : LocalDateTime = LocalDateTime.now()

  private val fetching = DBFetchingProcessRepository.create(db)

  private val activities = ProcessActivityRepository(db)

  private implicit val user: LoggedUser = TestFactory.adminUser()

  import dbProfile.api._

  test("fetch processes for category") {

    def saveProcessForCategory(cat :String) = {
      saveProcess(EspProcessBuilder
        .id(s"categorized-$cat")
        .exceptionHandler()
        .source("s", "")
        .emptySink("sink", ""),
        LocalDateTime.now(),
        category = cat
      )
    }
    val c1Reader = TestFactory.user(permissions = "c1"->Permission.Read)

    saveProcessForCategory("c1")
    saveProcessForCategory("c2")
    val processes= fetching.fetchProcesses()(ProcessShapeFetchStrategy.NotFetch, c1Reader, implicitly[ExecutionContext]).futureValue

    processes.map(_.name) shouldEqual "categorized-c1"::Nil
  }

  test("should rename process") {
    val oldName = ProcessName("oldName")
    val oldName2 = ProcessName("oldName2")
    val newName = ProcessName("newName")

    saveProcess(EspProcessBuilder
      .id(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )
    saveProcess(EspProcessBuilder
      .id(oldName2.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )

    processExists(oldName) shouldBe true
    processExists(oldName2) shouldBe true
    processExists(newName) shouldBe false

    val before = fetchMetaDataIdsForAllVersions(oldName)
    before.toSet shouldBe Set(oldName.value)

    renameProcess(oldName, newName.value) shouldBe 'right

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

    saveProcess(EspProcessBuilder
      .id(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )
    processExists(newName) shouldBe false

    renameProcess(oldName, newName.value) shouldBe 'right

    val comments = fetching.fetchProcessId(newName)
      .flatMap(v => activities.findActivity(ProcessIdWithName(v.get, newName)).map(_.comments))
      .futureValue

    atLeast(1, comments) should matchPattern {
      case Comment(_, "newName", 1L, "Rename: [oldName] -> [newName]", user.username, _) =>
    }
  }

  test("should prevent rename to existing name") {
    val oldName = ProcessName("oldName")
    val existingName = ProcessName("existingName")

    saveProcess(EspProcessBuilder
      .id(oldName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )
    saveProcess(EspProcessBuilder
      .id(existingName.value)
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )

    processExists(oldName) shouldBe true
    processExists(existingName) shouldBe true

    renameProcess(oldName, existingName.value) shouldBe ProcessAlreadyExists(existingName.value).asLeft
  }

  test("should generate new process version id based on latest version id") {

    val processName = ProcessName("processName")
    val latestVersionId = 4
    val now = LocalDateTime.now()
    val espProcess = EspProcessBuilder
      .id(processName.value)
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", "")

    saveProcess(espProcess, now)

    val firstProcessVersion = fetchLatestProcessVersion(processName)
    firstProcessVersion.id shouldBe 1

    //change of id for version imitates situation where versionId is different from number of all process versions (ex. after manual JSON removal from DB)
    repositoryManager.runInTransaction(writingRepo.processVersionsTableNoJson
      .filter(v => v.id === firstProcessVersion.id && v.processId === firstProcessVersion.processId)
      .map(_.id).update(latestVersionId))

    val latestProcessVersion = fetchLatestProcessVersion(processName)
    latestProcessVersion.id shouldBe latestVersionId

    val ProcessUpdated(oldVersionInfoOpt, newVersionInfoOpt) = updateProcess(latestProcessVersion.copy(json = Some("{}")))
    oldVersionInfoOpt shouldBe 'defined
    oldVersionInfoOpt.get.id shouldBe latestVersionId
    newVersionInfoOpt shouldBe 'defined
    newVersionInfoOpt.get.id shouldBe (latestVersionId + 1)

  }

  private def processExists(processName: ProcessName): Boolean = {
    fetching.fetchProcessId(processName).futureValue.flatMap(
      fetching.fetchLatestProcessVersion[Unit](_).futureValue
    ).nonEmpty
  }

  private def updateProcess(processVersion: ProcessVersionEntityData): ProcessUpdated = {
    processVersion.json shouldBe 'defined
    val json = processVersion.json.get
    val action = UpdateProcessAction(ProcessId(processVersion.processId), GraphProcess(json), "")

    val processUpdated = repositoryManager.runInTransaction(writingRepo.updateProcess(action)).futureValue
    processUpdated shouldBe 'right
    processUpdated.right.get
  }

  private def saveProcess(espProcess: EspProcess, now: LocalDateTime, category: String = "") = {
    val json = ProcessMarshaller.toJson(ProcessCanonizer.canonize(espProcess)).noSpaces
    currentTime = now
    val action = CreateProcessAction(ProcessName(espProcess.id), category, GraphProcess(json), TestProcessingTypes.Streaming, false)

    repositoryManager.runInTransaction(writingRepo.saveNewProcess(action)).futureValue shouldBe 'right
  }

  private def renameProcess(processName: ProcessName, newName: String) = {
    val processId = fetching.fetchProcessId(processName).futureValue.get
    repositoryManager.runInTransaction(writingRepo.renameProcess(ProcessIdWithName(processId, processName), newName)).futureValue
  }

  private def fetchMetaDataIdsForAllVersions(name: ProcessName) = {
    fetching.fetchProcessId(name).futureValue.toSeq.flatMap { processId =>
      fetching.fetchAllProcessesDetails[DisplayableProcess]().futureValue
        .filter(_.processId.value == processId.value)
        .flatMap(_.json.toSeq)
        .map(_.metaData.id)
    }
  }

  private def fetchLatestProcessVersion(name: ProcessName): ProcessVersionEntityData = {
    val fetchedProcess = fetching.fetchProcessId(name).futureValue.flatMap(
      fetching.fetchLatestProcessVersion[Unit](_).futureValue
    )
    fetchedProcess shouldBe 'defined
    fetchedProcess.get
  }
}
