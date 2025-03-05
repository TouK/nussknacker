package pl.touk.nussknacker.ui.process.fragment

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.sampleFragmentName
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

class FragmentRepositorySpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with BeforeAndAfterEach
    with NuResourcesTest
    with VeryPatientScalaFutures {

  it should "load fragments" in {
    val sampleFragment  = CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment)
    val sampleFragment2 = CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment2)

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

  it should "return None for missing fragment" in {
    fragmentRepository.fetchLatestFragment(ProcessName("non-existing-fragment"))(adminUser).futureValue shouldBe None
  }

}
