package pl.touk.nussknacker.ui.process.repository

import java.time.{LocalDate, LocalDateTime}

import argonaut.PrettyParams
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestPermissions, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessAlreadyExists
import pl.touk.nussknacker.ui.security.api.Permission

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DBFetchingProcessRepositorySpec
  extends FunSuite
    with Matchers
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with WithHsqlDbTesting
    with ScalaFutures
    with TestPermissions {
  import cats.syntax.either._

  implicit val defaultPatience = PatienceConfig(timeout = Span(1, Seconds), interval = Span(5, Millis))

  private val writingRepo = new DbWriteProcessRepository[Future](db, Map(TestProcessingTypes.Streaming -> 0))
    with WriteProcessRepository with BasicRepository {
    override protected def now: LocalDateTime = currentTime
  }
  private var currentTime : LocalDateTime = LocalDateTime.now()

  private val fetching = DBFetchingProcessRepository.create(db)

  private implicit val user = TestFactory.user(testPermissionAdmin)

  test("ignore subprocessesModificationDate when no subprocesses") {

    saveProcess(EspProcessBuilder
      .id("noSubprocess")
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .emptySink("s2", ""),
      LocalDateTime.now()
    )

    fetchSubprocessesModificationDate("noSubprocess") shouldBe Some(Map())
  }

  test("ignore subprocessesModificationDate for subprocess with fixed version") {

    saveSubProcess("sub1", minusDays(1))
    saveSubProcess("sub2", minusDays(2))

    saveProcess(EspProcessBuilder
      .id("fixedSubprocess")
      .subprocessVersions(Map("sub1" -> 3L))
      .exceptionHandler()
      .source("s", "")
      .subprocessOneOut("s1", "sub1", "out1")
      .subprocess("s2", "sub2", List(), Map()),
      LocalDateTime.now()
    )

    fetchSubprocessesModificationDate("fixedSubprocess") shouldBe Some(Map("sub2" -> minusDays(2)))

  }
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
    val c1Reader = TestFactory.user("c1"->Permission.Read)

    saveProcessForCategory("c1")
    saveProcessForCategory("c2")
    val processes= fetching.fetchProcesses()(c1Reader, implicitly[ExecutionContext]).futureValue

    processes.map(_.name) shouldEqual "categorized-c1"::Nil
  }
  test("get last subprocessesModificationDate for subprocess with floating version") {

    saveSubProcess("sub1", minusDays(1))
    saveSubProcess("sub3", minusDays(3))

    saveProcess(EspProcessBuilder
      .id("floatingVersions")
      .exceptionHandler()
      .source("s", "")
      .subprocessOneOut("s1", "sub1", "out1")
      .subprocess("s3", "sub3", List(), Map()),
      LocalDateTime.now()
    )

    fetchSubprocessesModificationDate("floatingVersions") shouldBe Some(Map("sub1" -> minusDays(1), "sub3" -> minusDays(3)))

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

  private def minusDays(days: Int) : LocalDateTime = LocalDate.now().minusDays(days).atStartOfDay()

  private def fetchSubprocessesModificationDate(processName: String): Option[Map[String, LocalDateTime]] = {
    val processId = fetching.fetchProcessId(ProcessName(processName)).futureValue.get
    fetching.fetchLatestProcessDetailsForProcessId(processId).futureValue.get.subprocessesModificationDate
  }

  private def processExists(processName: ProcessName): Boolean = {
    fetching.fetchProcessId(processName).futureValue.flatMap(
      fetching.fetchLatestProcessVersion(_).futureValue
    ).nonEmpty
  }

  private def saveProcess(espProcess: EspProcess, now: LocalDateTime, category: String = "") = {
    val json = UiProcessMarshaller.toJson(ProcessCanonizer.canonize(espProcess)).nospaces
    currentTime = now
    writingRepo.saveNewProcess(ProcessName(espProcess.id), category, GraphProcess(json), TestProcessingTypes.Streaming, false).futureValue shouldBe 'right
  }

  private def renameProcess(processName: ProcessName, newName: String) = {
    val processId = fetching.fetchProcessId(processName).futureValue.get
    writingRepo.renameProcess(processId, newName).futureValue
  }

  private def saveSubProcess(id: String, now: LocalDateTime) = {
    val process = EspProcessBuilder.id(id).exceptionHandler().source("so", "").emptySink("si", "")
    val json = UiProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).nospaces
    currentTime = now
    writingRepo.saveNewProcess(ProcessName(id), "", GraphProcess(json), TestProcessingTypes.Streaming, true).futureValue shouldBe 'right
  }

  private def fetchMetaDataIdsForAllVersions(name: ProcessName) = {
    fetching.fetchProcessId(name).futureValue.toSeq.flatMap { processId =>
      fetching.fetchAllProcessesDetails().futureValue
        .filter(_.id == processId.value.toString)
        .flatMap(_.json.toSeq)
        .map(_.metaData.id)
    }
  }
}
