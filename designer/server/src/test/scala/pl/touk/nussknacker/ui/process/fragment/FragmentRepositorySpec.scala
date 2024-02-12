package pl.touk.nussknacker.ui.process.fragment

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.tests.ProcessTestData.sampleFragmentName
import pl.touk.nussknacker.tests.ProcessTestData
import pl.touk.nussknacker.tests.base.it.NuResourcesTest
import pl.touk.nussknacker.tests.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
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

    fragmentRepository.fetchLatestFragments(Streaming.stringify)(adminUser).futureValue shouldBe List(
      ProcessTestData.sampleFragment2
    )
  }

}
