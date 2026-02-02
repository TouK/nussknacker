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

@Slow
class RtmClientNearPosSpec
    extends AnyFreeSpecLike
    with BaseE2ESpec
    with Matchers
    with VeryPatientScalaFutures
    with BasicScenarioInformationFetching {

  // The RTMClientNearPOS is a highly dynamic scenario,
  // with output depending on the current weekday, time and randomly generated coordinates.
  // We currently only check, that the scenario is compiled and running)
  "RtmClientNearPos is running" in {
    val scenarios = fetchScenariosBasicInformation()
    scenarios should contain(
      ScenarioBasicInformation(
        name = "RTMClientNearPOS",
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
    client.purgeKafkaTopic("GeoLocations")
    client.purgeKafkaTopic("GeoLocationsOutputEmail")
    client.purgeKafkaTopic("GeoLocationsOutputSms")
    client.purgeKafkaTopic("GeoLocationsOutputPush")
    super.afterEach()
  }

}
