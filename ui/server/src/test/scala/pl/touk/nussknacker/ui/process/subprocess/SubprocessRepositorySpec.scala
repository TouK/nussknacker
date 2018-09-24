package pl.touk.nussknacker.ui.process.subprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.ui.api.ProcessTestData
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.language.higherKinds

class SubprocessRepositorySpec extends FlatSpec with ScalatestRouteTest with Matchers with ScalaFutures with BeforeAndAfterEach with EspItTest with Eventually {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

  import pl.touk.nussknacker.ui.api.helpers.TestFactory.testCategoryName
  it should "fetches subprocess by its version" in {
    val sampleSubprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming)
    val sampleSubprocess2 = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess2, TestProcessingTypes.Streaming)
    saveSubProcess(sampleSubprocess) { status shouldEqual StatusCodes.OK }
    updateProcess(sampleSubprocess2) { status shouldEqual StatusCodes.OK }

    ProcessTestData.sampleSubprocess.metaData.id shouldBe ProcessTestData.sampleSubprocess2.metaData.id
    ProcessTestData.sampleSubprocess should not be ProcessTestData.sampleSubprocess2

    subprocessRepository.loadSubprocesses() shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, testCategoryName))
    val subprocessId = ProcessTestData.sampleSubprocess.metaData.id
    subprocessRepository.loadSubprocesses(Map(subprocessId -> 1)) shouldBe Set(SubprocessDetails(ProcessTestData.emptySubprocess, testCategoryName))
    subprocessRepository.loadSubprocesses(Map(subprocessId -> 2)) shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess, testCategoryName))
    subprocessRepository.loadSubprocesses(Map(subprocessId -> 3)) shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, testCategoryName))
    subprocessRepository.loadSubprocesses() shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, testCategoryName))
  }

}
