package pl.touk.nussknacker

import better.files.File
import org.scalatest.freespec.AnyFreeSpecLike

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample {

  "A test" in {
    sendMessageToKafka("transactions", """{ "message": "Test1" }""")
    sendMessageToKafka("transactions", """{ "message": "Test2" }""")

    nussknackerAppClient.scenarioFromFile(File(getClass.getResource("/scenarios/DetectLargeTransactions.json")))

    println("test")
  }

}
