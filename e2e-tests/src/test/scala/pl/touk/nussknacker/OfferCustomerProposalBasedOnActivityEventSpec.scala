package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.utils.BasicScenarioInformationFetching
import pl.touk.nussknacker.utils.BasicScenarioInformationFetching.{
  ScenarioBasicInformation,
  ScenarioState,
  ScenarioStatus
}

class OfferCustomerProposalBasedOnActivityEventSpec
    extends AnyFreeSpecLike
    with BaseE2ESpec
    with Matchers
    with BasicScenarioInformationFetching {

  // The OfferCustomerProposalBasedOnActivityEvent is a highly dynamic scenario,
  // with output depending on events representing customers actions.
  // We currently only check, that the scenario is compiled and running)
  "OfferCustomerProposalBasedOnActivityEvent scenario is running" in {
    val scenarios = fetchScenariosBasicInformation()
    scenarios should contain(
      ScenarioBasicInformation(
        name = "OfferCustomerProposalBasedOnActivityEvent",
        isArchived = false,
        isFragment = false,
        processingType = "streaming",
        processCategory = "Default",
        labels = List.empty,
        state = ScenarioState(ScenarioStatus("RUNNING"))
      )
    )
  }

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("CustomerEvents")
    client.purgeKafkaTopic("OfferProposalsBasedOnCustomerEvents")
    super.afterEach()
  }

}
