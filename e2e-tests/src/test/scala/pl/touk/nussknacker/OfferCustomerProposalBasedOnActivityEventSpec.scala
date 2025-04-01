package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class OfferCustomerProposalBasedOnActivityEventSpec
    extends AnyFreeSpecLike
    with BaseE2ESpec
    with Matchers
    with VeryPatientScalaFutures {

  // The OfferCustomerProposalBasedOnActivityEvent is a highly dynamic scenario, with output depending on events representing customers actions
  // We currently only check, that the scenario is compiled and running (which is done by the underlying nussknacker-example-scenarios-library Docker image)
  "OfferCustomerProposalBasedOnActivityEventSpec" in {}

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("CustomerEvents")
    client.purgeKafkaTopic("OfferProposalsBasedOnCustomerEvents")
    super.afterEach()
  }

}
