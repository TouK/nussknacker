package pl.touk.nussknacker.engine.example

import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneId}

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers, Suite}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.spel

trait ExampleItTest1 extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually with ExampleItTest { self: BaseITest =>

  import KafkaUtils._
  import spel.Implicits._

  def process() =
    EspProcessBuilder
      .id("example1")
      .parallelism(1)
      .exceptionHandler("sampleParam" -> "sampleParamValue")
      .source("start", "kafka-transaction", "topic" -> "topic.transaction")
      .filter("amountFilter", "#input.amount > 1")
      .sink("end", "#UTIL.mapToJson({'clientId': #input.clientId, 'amount': #input.amount})",
        "kafka-stringSink", "topic" -> "topic.out")

  it should "filter events and save to kafka" in {
    sendTransaction(Transaction("ClientA", 1))
    sendTransaction(Transaction("ClientB", 2))

    registrar.register(env, process())
    env.execute(process().id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume("topic.out").take(1).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
    processed shouldBe List(
      """{"clientId":"ClientB","amount":"2"}"""
    )
  }
}

trait ExampleItTest2 extends FlatSpec with BeforeAndAfterAll with Matchers with BaseITest with Eventually with ExampleItTest { self: BaseITest =>

  import KafkaUtils._
  import spel.Implicits._

  def process() =
    EspProcessBuilder
      .id("example2")
      .parallelism(1)
      .exceptionHandler("sampleParam" -> "sampleParamValue")
      .source("start", "kafka-transaction", "topic" -> "topic.transaction")
      .enricher("clientEnricher", "client", "clientService", "clientId" -> "#input.clientId")
      .sink("end",
        "#UTIL.mapToJson({'clientId': #input.clientId, 'clientName': #client.name, 'cardNumber': #client.cardNumber})",
        "kafka-stringSink", "topic" -> "topic.out"
      )

  it should "enrich transaction events with client data" in {
    //this event triggers clientEnricher exception which will be logged
    sendTransaction(Transaction("ClientX", 3))

    //these are happy path events
    sendTransaction(Transaction("ClientA", 1))
    sendTransaction(Transaction("ClientB", 2))

    registrar.register(env, process())
    env.execute(process().id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume("topic.out").take(2).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
    processed.toSet shouldBe Set(
      """{"clientId":"ClientA","clientName":"Alice","cardNumber":"123"}""",
      """{"clientId":"ClientB","clientName":"Bob","cardNumber":"234"}"""
    )
  }
}

trait ExampleItTest3 extends FlatSpec with BeforeAndAfterAll with Matchers with BaseITest with Eventually with ExampleItTest { self: BaseITest =>

  import KafkaUtils._
  import spel.Implicits._

  def process() =
    EspProcessBuilder
      .id("example3")
      .parallelism(1)
      .exceptionHandler("sampleParam" -> "sampleParamValue")
      .source("start", "kafka-transaction", "topic" -> "topic.transaction")
      .customNode("aggregate", "aggregatedAmount", "transactionAmountAggregator", "clientId" -> "#input.clientId")
      .filter("aggregateFilter", "#aggregatedAmount.amount > 10")
      .sink("end",
        "#UTIL.mapToJson({'clientId': #input.clientId, 'aggregatedAmount': #aggregatedAmount.amount})",
        "kafka-stringSink", "topic" -> "topic.out"
      )

  it should "perform transaction amount aggregation for every client" in {
    sendTransaction(Transaction("ClientA", 2))
    sendTransaction(Transaction("ClientA", 9))
    sendTransaction(Transaction("ClientB", 1))
    sendTransaction(Transaction("ClientB", 9))
    sendTransaction(Transaction("ClientC", 13))

    registrar.register(env, process())
    env.execute(process().id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume("topic.out").take(2).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
    processed.toSet shouldBe Set(
      """{"clientId":"ClientA","aggregatedAmount":"11"}""",
      """{"clientId":"ClientC","aggregatedAmount":"13"}"""
    )
  }
}


trait ExampleItTest4 extends FlatSpec with BeforeAndAfterAll with Matchers with BaseITest with Eventually with ExampleItTest { self: BaseITest =>

  import KafkaUtils._
  import spel.Implicits._

  def process() =
    EspProcessBuilder
      .id("example4")
      .parallelism(1)
      .exceptionHandler("sampleParam" -> "sampleParamValue")
      .source("start", "kafka-transaction", "topic" -> "topic.transaction")
      .customNode("transactionCounter", "transactionCounts", "eventsCounter", "key" -> "#input.clientId", "length" -> "'1h'")
      .filter("aggregateFilter", "#transactionCounts.count > 1")
      .sink("end",
        "#UTIL.mapToJson({'clientId': #input.clientId, 'transactionsCount': #transactionCounts.count})",
        "kafka-stringSink", "topic" -> "topic.out"
      )

  it should "count transactions in 1h window" in {
    val now = LocalDateTime.of(2017, 1, 1, 10, 0)
    val windowLengthMinutes = 60

    //transactions for clientId=ClientA are within window so will pass whole process
    sendTransaction(Transaction("ClientA", 1, toEpoch(now)))
    sendTransaction(Transaction("ClientA", 2, toEpoch(now.plusMinutes(windowLengthMinutes - 10))))

    //transactions for clientId=ClientB are NOT within window so will NOT pass whole process
    sendTransaction(Transaction("ClientB", 1, toEpoch(now)))
    sendTransaction(Transaction("ClientB", 2, toEpoch(now.plusMinutes(windowLengthMinutes + 2))))

    //transactions for clientId=ClientC are within window so will pass whole process
    sendTransaction(Transaction("ClientC", 1, toEpoch(now.plusMinutes(72))))
    sendTransaction(Transaction("ClientC", 2, toEpoch(now.plusMinutes(72 + windowLengthMinutes - 2))))

    registrar.register(env, process())
    env.execute(process().id)

    val consumer = kafkaClient.createConsumer()
    val processed = consumer.consume("topic.out").take(2).toList.map(msg => new String(msg.message(), StandardCharsets.UTF_8))
    processed.toSet shouldBe Set(
      """{"clientId":"ClientA","transactionsCount":"2"}""",
      """{"clientId":"ClientC","transactionsCount":"2"}"""
    )
  }

}

trait ExampleItTest { self: KafkaSpec =>

  def sendTransaction(t: Transaction) = {
    kafkaClient.sendMessage("topic.transaction", t.display.nospaces)
  }

  def toEpoch(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

}

class ExampleItTest1Java extends ExampleItTest1 with BaseJavaITest
class ExampleItTest2Java extends ExampleItTest2 with BaseJavaITest
class ExampleItTest3Java extends ExampleItTest3 with BaseJavaITest
class ExampleItTest4Java extends ExampleItTest4 with BaseJavaITest

class ExampleItTest1Scala extends ExampleItTest1 with BaseScalaITest
class ExampleItTest2Scala extends ExampleItTest2 with BaseScalaITest
class ExampleItTest3Scala extends ExampleItTest3 with BaseScalaITest
class ExampleItTest4Scala extends ExampleItTest4 with BaseScalaITest