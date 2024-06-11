package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample {

  "A test" in {
    sendMessageToKafka("transactions", """{ "message": "Test1" }""")
    sendMessageToKafka("transactions", """{ "message": "Test2" }""")

    nussknackerAppClient.scenarioFromFile(getClass.getResourceAsStream("DetectLargeTransactions.json"))

    println("test")
  }

}
