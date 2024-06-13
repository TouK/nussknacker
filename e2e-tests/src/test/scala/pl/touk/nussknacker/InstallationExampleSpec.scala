package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike

import java.util.UUID

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample {

  "A test" in {
    nussknackerAppClient.loadFlinkStreamingScenarioFromResource("DetectLargeTransactions")
    nussknackerAppClient.deploy("DetectLargeTransactions", UUID.randomUUID())
    val deploymentId = UUID.randomUUID()
    nussknackerAppClient.deploy("DetectLargeTransactions", deploymentId)
    nussknackerAppClient.waitForRunningState(deploymentId)

    sendMessageToKafka("transactions", """{ "clientId": "100", "amount":100, "isLast":false }" """)
    sendMessageToKafka("transactions", """{ "clientId": "101", "amount":1000, "isLast":false }" """)
    sendMessageToKafka("transactions", """{ "clientId": "102", "amount":10000, "isLast":false }" """)

    println("test") // todo: remove
  }

}
