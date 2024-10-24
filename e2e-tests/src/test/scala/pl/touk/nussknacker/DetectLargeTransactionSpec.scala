package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class DetectLargeTransactionSpec extends AnyFreeSpecLike with BaseE2ESpec with Matchers with VeryPatientScalaFutures {

  "Large transactions should be properly detected" ignore {
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
      client.sendMessageToKafka("Transactions", transaction)
    }

    eventually {
      val processedTransactions = client.readAllMessagesFromKafka("ProcessedTransactions")
      processedTransactions should equal(largeAmountTransactions)
    }
  }

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("Transactions")
    client.purgeKafkaTopic("ProcessedTransactions")
    super.afterEach()
  }

  private def transactionJson(amount: Int) =
    ujson.Obj("clientId" -> "100", "amount" -> amount, "isLast" -> false)
}
