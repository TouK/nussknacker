package pl.touk.nussknacker

import better.files._
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample with Matchers {

  "A test" in {
    loadFlinkStreamingScenarioFromResource(
      scenarioName = "DetectLargeTransactions",
      scenarioJsonFile = File(getClass.getResource("/scenarios/DetectLargeTransactions.json"))
    )
    deployAndWaitForRunningState(scenarioName = "DetectLargeTransactions")

    val transactions = List(
      ujson.read("""{ "clientId": "100", "amount":100, "isLast":false }"""),
      ujson.read("""{ "clientId": "101", "amount":1000, "isLast":false }"""),
      ujson.read("""{ "clientId": "102", "amount":10000, "isLast":false }""")
    )

    transactions.foreach { transaction =>
      sendMessageToKafka("transactions", transaction)
    }

    Thread.sleep(5000)

    val processedTransactions = readMessagesFromKafka("transactions")

    processedTransactions should equal(transactions)
  }

}
