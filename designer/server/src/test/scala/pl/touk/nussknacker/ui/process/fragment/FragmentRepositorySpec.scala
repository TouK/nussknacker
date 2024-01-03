package pl.touk.nussknacker.ui.process.fragment

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.language.higherKinds

class FragmentRepositorySpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with BeforeAndAfterEach
    with NuResourcesTest
    with VeryPatientScalaFutures {

  import pl.touk.nussknacker.ui.api.helpers.TestCategories._

  it should "load fragments" in {
    val sampleFragment =
      ProcessConverter.toDisplayable(ProcessTestData.sampleFragment, TestProcessingTypes.Streaming, Category1)
    val sampleFragment2 =
      ProcessConverter.toDisplayable(ProcessTestData.sampleFragment2, TestProcessingTypes.Streaming, Category1)
    savefragment(sampleFragment) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(sampleFragment2) {
      status shouldEqual StatusCodes.OK
    }

    ProcessTestData.sampleFragment.name shouldBe ProcessTestData.sampleFragment2.name
    ProcessTestData.sampleFragment should not be ProcessTestData.sampleFragment2

    fragmentRepository.fetchLatestFragments(TestProcessingTypes.Streaming)(adminUser).futureValue shouldBe List(
      FragmentDetails(ProcessTestData.sampleFragment2, Category1)
    )
  }

}
