package pl.touk.nussknacker.ui.statistics

import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.statistics.repository.FingerprintRepositoryImpl

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class FingerprintServiceTest
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with PatientScalaFutures
    with WithHsqlDbTesting {

  private implicit val runner: DBIOActionRunner = DBIOActionRunner(testDbRef)
  private val repository                        = new FingerprintRepositoryImpl(testDbRef)
  private val sut                               = new FingerprintService(repository, randomFingerprintFileName)

  test("should return a fingerprint from the configuration") {
    val config = UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, Some("set via config"), None)

    val fingerprint = sut.fingerprint(config).futureValue

    fingerprint.value.value shouldBe "set via config"
  }

  test("should generate a random fingerprint if the configured one is blank") {
    runner.run(repository.read()).futureValue shouldBe None

    val fingerprint = sut.fingerprint(config).futureValue.value

    runner.run(repository.read()).futureValue shouldBe Some(fingerprint)
    fingerprint.value should fullyMatch regex "gen-\\w{10}"
  }

  test("should return a fingerprint from the database") {
    runner.runInTransaction(repository.readOrSave(new Fingerprint("db stored"))).futureValue
    runner.run(repository.read()).futureValue shouldBe Some(new Fingerprint("db stored"))

    val fingerprint = sut.fingerprint(config).futureValue

    fingerprint.value.value shouldBe "db stored"
  }

  test("should return a fingerprint from a file and save it in the database") {
    runner.run(repository.read()).futureValue shouldBe None

    val fingerprintFile = getTempFileLocation
    writeContentToFile(fingerprintFile, "file stored")

    val fingerprint = new FingerprintService(repository, new FileName(fingerprintFile.getName))
      .fingerprint(config)
      .futureValue

    fingerprint.value.value shouldBe "file stored"
    runner.run(repository.read()).futureValue shouldBe Some(new Fingerprint("file stored"))
  }

  private val config = UsageStatisticsReportsConfig(enabled = true, errorReportsEnabled = true, None, None)

  private def getTempFileLocation: File = {
    val file = new File(System.getProperty("java.io.tmpdir"), randomFingerprintFileName.value)
    file.deleteOnExit()
    file
  }

  private def randomFingerprintFileName: FileName = new FileName(s"nussknacker-${UUID.randomUUID()}.fingerprint")

  private def writeContentToFile(file: File, content: String): Unit = {
    Files.write(file.toPath, content.getBytes(StandardCharsets.UTF_8))
  }

}
