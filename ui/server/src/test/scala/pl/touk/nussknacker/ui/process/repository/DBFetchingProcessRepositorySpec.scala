package pl.touk.nussknacker.ui.process.repository

import java.time.{LocalDate, LocalDateTime}

import argonaut.PrettyParams
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, WithDbTesting}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DBFetchingProcessRepositorySpec extends FlatSpec with Matchers with BeforeAndAfterEach with WithDbTesting with ScalaFutures {

  implicit val defaultPatience = PatienceConfig(timeout = Span(1, Seconds), interval = Span(5, Millis))

  private val writingRepo = new DbWriteProcessRepository[Future](db, Map(ProcessingType.Streaming -> 0))
    with WriteProcessRepository with BasicRepository {
    override protected def now: LocalDateTime = currentTime
  }
  private var currentTime : LocalDateTime = LocalDateTime.now()

  private val fetching = DBFetchingProcessRepository.create(db)

  private implicit val user = TestFactory.user(Permission.Admin)

  it should "ignore subprocessesModificationDate when no subprocesses" in {

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

  it should "ignore subprocessesModificationDate for subprocess with fixed version" in {

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

  it should "get last subprocessesModificationDate for subprocess with floating version" in {

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
                  
  private def minusDays(days: Int) : LocalDateTime = LocalDate.now().minusDays(days).atStartOfDay()

  private def fetchSubprocessesModificationDate(processId: String): Option[Map[String, LocalDateTime]] =
    fetching.fetchLatestProcessDetailsForProcessId(processId).futureValue.get.subprocessesModificationDate

  private def saveProcess(espProcess: EspProcess, now: LocalDateTime) = {
    val json = UiProcessMarshaller.toJson(ProcessCanonizer.canonize(espProcess), PrettyParams.nospace)
    currentTime = now
    writingRepo.saveNewProcess(espProcess.id, "", GraphProcess(json), ProcessingType.Streaming, false).futureValue shouldBe 'right
  }

  private def saveSubProcess(id: String, now: LocalDateTime) = {
    val process = EspProcessBuilder.id(id).exceptionHandler().source("so", "").emptySink("si", "")
    val json = UiProcessMarshaller.toJson(ProcessCanonizer.canonize(process), PrettyParams.nospace)
    currentTime = now
    writingRepo.saveNewProcess(id, "", GraphProcess(json), ProcessingType.Streaming, true).futureValue shouldBe 'right
  }


}
