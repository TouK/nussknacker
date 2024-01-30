package pl.touk.nussknacker.ui.process.fragment

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.sampleFragmentName
import pl.touk.nussknacker.ui.api.helpers.{NuResourcesTest, ProcessTestData, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

import scala.language.higherKinds

class FragmentRepositorySpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with BeforeAndAfterEach
    with NuResourcesTest
    with VeryPatientScalaFutures {

  it should "load fragments" in {
    val sampleFragment =
      CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment)
    val sampleFragment2 =
      CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment2)
    saveFragment(sampleFragment) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(sampleFragment2, sampleFragmentName) {
      status shouldEqual StatusCodes.OK
    }

    fragmentRepository.fetchLatestFragments(TestProcessingTypes.Streaming)(adminUser).futureValue shouldBe List(
      ProcessTestData.sampleFragment2
    )
  }

}
