package pl.touk.nussknacker.ui.process.fragment

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.ProcessTestData.sampleFragmentName

class FragmentRepositorySpec
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with BeforeAndAfterEach
    with NuResourcesTest
    with VeryPatientScalaFutures {

  it should "load fragments" in {
    val sampleFragment  = ProcessTestData.sampleFragment.toScenarioGraph
    val sampleFragment2 = ProcessTestData.sampleFragment2.toScenarioGraph

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
