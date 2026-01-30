package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.utils.BasicScenarioInformationFetching
import pl.touk.nussknacker.utils.BasicScenarioInformationFetching.{
  ScenarioBasicInformation,
  ScenarioState,
  ScenarioStatus
}
import ujson.Str

@Slow
class OfferCustomerProposalBasedOnActivityEventSpec
    extends AnyFreeSpecLike
    with BaseE2ESpec
    with Matchers
    with VeryPatientScalaFutures
    with BasicScenarioInformationFetching {

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

  "Generate offers only for of-age customer and ClientBrowseOffers event" in {
    val events = List(
      ujson.Obj(
        "customerId" -> "1",
        "eventType"  -> "ClientBrowseOffers"
      ),
      ujson.Obj(
        "customerId"    -> "2",
        "assignedOffer" -> "ClientBrowseOffers"
      )
    )

    events.foreach { customer =>
      client.sendMessageToKafka("CustomerEvents", customer)
    }

    eventually {
      val offers = client.readAllMessagesFromKafka("OfferProposalsBasedOnCustomerEvents")

      offers.length shouldBe 1

      val offer = offers.head.obj.toMap
      offer("customerId") shouldBe Str("1")
      offer("profileId") shouldBe Str("111")

      // Assert, that fields with dynamic values are present
      offer.keys should contain allOf (
        "offerName",
        "preparedMessageReadyToSend",
        "clientMsisdn",
        "dueDate",
        "clientName",
        "offerDescription",
      )
    }
  }

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("CustomerEvents")
    client.purgeKafkaTopic("OfferProposalsBasedOnCustomerEvents")
    super.afterEach()
  }

}
