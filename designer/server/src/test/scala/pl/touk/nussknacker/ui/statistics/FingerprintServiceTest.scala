package pl.touk.nussknacker.ui.statistics

import db.util.DBIOActionInstances.DB
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepository
import slick.dbio.{DBIOAction, SuccessAction}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FingerprintServiceTest extends AnyFunSuite with Matchers with OptionValues with PatientScalaFutures {

  private val repository = mock[FingerprintRepository[DB]]
  private val sut        = new FingerprintService(new DummyDbioActionRunner, repository)

  test("should return a fingerprint from the configuration") {
    val config = UsageStatisticsReportsConfig(enabled = true, Some("set via config"), None)

    val fingerprint = sut.fingerprint(config, randomFingerprintFileName).futureValue

    fingerprint.value shouldBe "set via config"
  }

  test("should generate a random fingerprint if the configured one is blank") {
    when(repository.read()).thenReturn(DBIOAction.successful(None))
    when(repository.write(any[String]())).thenAnswer(_ => DummyDbioActionRunner.unitAction)

    val fingerprint = sut.fingerprint(config, randomFingerprintFileName).futureValue

    fingerprint.value should fullyMatch regex "gen-\\w{10}"
  }

  test("should return a fingerprint from the database") {
    when(repository.read()).thenReturn(DBIOAction.successful(Some("db stored")))

    val fingerprint = sut.fingerprint(config, randomFingerprintFileName).futureValue

    fingerprint.value shouldBe "db stored"
    verify(repository, never()).write(ArgumentMatchers.eq("db stored"))
  }

  test("should return a fingerprint from a file and save it in the database") {
    when(repository.read()).thenReturn(DBIOAction.successful(None))
    val fingerprintFile = getTempFileLocation
    writeContentToFile(fingerprintFile, "file stored")
    when(repository.write(ArgumentMatchers.eq("file stored"))).thenAnswer(_ => DummyDbioActionRunner.unitAction)

    val fingerprint = sut.fingerprint(config, new FileName(fingerprintFile.getName)).futureValue

    fingerprint.value shouldBe "file stored"
    verify(repository, times(1)).write(ArgumentMatchers.eq("file stored"))
  }

  private val config = UsageStatisticsReportsConfig(enabled = true, None, None)

  private def getTempFileLocation: File = {
    val file = new File(System.getProperty("java.io.tmpdir"), randomFingerprintFileName.value)
    file.deleteOnExit()
    file
  }

  private def randomFingerprintFileName: FileName = new FileName(s"nussknacker-${UUID.randomUUID()}.fingerprint")

  private def writeContentToFile(file: File, content: String): Unit = {
    Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8))
  }

  class DummyDbioActionRunner extends DBIOActionRunner(mock[DbRef]) {

    override def run[T](action: DB[T]): Future[T] = action match {
      case SuccessAction(v) => Future.successful(v)
      case _                => throw new IllegalStateException("Cannot run DB action")
    }

    override def runInTransaction[T](action: DB[T]): Future[T] = run(action)
  }

  object DummyDbioActionRunner {
    val unitAction = DBIOAction.successful(())
  }

}
