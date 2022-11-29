package pl.touk.nussknacker.ui.process.subprocess

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.language.higherKinds

class SubprocessRepositorySpec extends AnyFlatSpec with ScalatestRouteTest with Matchers with BeforeAndAfterEach with EspItTest with VeryPatientScalaFutures {

  import pl.touk.nussknacker.ui.api.helpers.TestCategories._

  it should "fetches fragment by its version" in {
    val sampleSubprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming, TestCat)
    val sampleSubprocess2 = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess2, TestProcessingTypes.Streaming, TestCat)
    saveSubProcess(sampleSubprocess) { status shouldEqual StatusCodes.OK }
    updateProcess(sampleSubprocess2) { status shouldEqual StatusCodes.OK }

    ProcessTestData.sampleSubprocess.metaData.id shouldBe ProcessTestData.sampleSubprocess2.metaData.id
    ProcessTestData.sampleSubprocess should not be ProcessTestData.sampleSubprocess2

    subprocessRepository.loadSubprocesses() shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, TestCat))
    val subprocessId = ProcessTestData.sampleSubprocess.metaData.id
    subprocessRepository.loadSubprocesses(Map(subprocessId -> VersionId(1))) shouldBe Set(SubprocessDetails(ProcessTestData.emptySubprocess, TestCat))
    subprocessRepository.loadSubprocesses(Map(subprocessId -> VersionId(2))) shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess, TestCat))
    subprocessRepository.loadSubprocesses(Map(subprocessId -> VersionId(3))) shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, TestCat))
    subprocessRepository.loadSubprocesses() shouldBe Set(SubprocessDetails(ProcessTestData.sampleSubprocess2, TestCat))
  }

}
