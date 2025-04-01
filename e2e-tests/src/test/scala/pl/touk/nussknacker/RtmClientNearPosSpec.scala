package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class RtmClientNearPosSpec extends AnyFreeSpecLike with BaseE2ESpec with Matchers with VeryPatientScalaFutures {

  // The RTMClientNearPOS is a highly dynamic scenario, with output depending on the current weekday and time
  // We currently only check, that the scenario is compiled and running (which is done by the underlying nussknacker-example-scenarios-library Docker image)
  "RtmClientNearPosSpec" in {}

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("GeoLocations")
    client.purgeKafkaTopic("GeoLocationsOutputEmail")
    client.purgeKafkaTopic("GeoLocationsOutputSms")
    client.purgeKafkaTopic("GeoLocationsOutputPush")
    super.afterEach()
  }

}
