package pl.touk.nussknacker

import better.files._
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class InstallationExampleSpecNuEnvironment
    extends AnyFreeSpecLike
    with DockerBasedInstallationExampleNuEnvironment
    with Matchers
    with VeryPatientScalaFutures {

  override def afterStart(): Unit = {
    super.afterStart()
    val scenarioName = "InstallationExampleDetectLargeTransactions"
    loadFlinkStreamingScenarioFromResource(
      scenarioName = scenarioName,
      scenarioJsonFile = File(getClass.getResource(s"/scenarios/$scenarioName.json"))
    )
    deployAndWaitForRunningState(scenarioName)
  }

  "Make sure that the installation example is functional" in {
    val smallAmountTransactions = List(
      transactionJson(amount = 1),
      transactionJson(amount = 2),
      transactionJson(amount = 3),
    )
    val largeAmountTransactions = List(
      transactionJson(amount = 100),
      transactionJson(amount = 1000),
      transactionJson(amount = 10000),
    )

    (smallAmountTransactions ::: largeAmountTransactions).foreach { transaction =>
      sendMessageToKafka("transactions", transaction)
    }

    eventually {
      val processedTransactions = readAllMessagesFromKafka("processedEvents")
      processedTransactions should equal(largeAmountTransactions)
    }
  }

  override protected def afterEach(): Unit = {
    purgeKafkaTopic("transactions")
    purgeKafkaTopic("processedEvents")
    super.afterEach()
  }

  private def transactionJson(amount: Int) =
    ujson.read(s"""{ "clientId": "100", "amount":$amount, "isLast":false }""")
}
