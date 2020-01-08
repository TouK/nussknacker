package pl.touk.nussknacker.engine.demo

import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneId}

import org.apache.kafka.clients.producer.RecordMetadata
import org.scalatest.{BeforeAndAfterAll, Matchers, Outcome, fixture}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaUtils
import pl.touk.nussknacker.engine.spel

import scala.concurrent.Future

//TODO: do we currently need these tests?
trait ExampleItTests extends fixture.FunSuite with BeforeAndAfterAll with Matchers { self: BaseITest =>

  import KafkaUtils._
  import spel.Implicits._

  override type FixtureParam = String

  //to be able to run tests concurrently/not care about cleaning, we use different topics
  override protected def withFixture(test: OneArgTest): Outcome = {
    val topicPrefix = s"topic.for.test.${test.name.replace(" ", "_")}."
    withFixture(test.toNoArgTest(topicPrefix))
  }

  test("filter events and save to kafka") { topicPrefix =>
    val in = topicPrefix + "in"
    val out = topicPrefix + "out"

    val process = EspProcessBuilder
      .id("example1")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-transaction", "topic" -> s"'$in'")
      .filter("amountFilter", "#input.amount > 1")
      .sink("end", "#UTIL.mapToJson({'clientId': #input.clientId, 'amount': #input.amount})",
        "kafka-stringSink", "topic" -> s"'$out'")


    sendTransaction(in, Transaction("ClientA", 1))
    sendTransaction(in, Transaction("ClientB", 2))

    register(process) {

      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(out).take(1).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
      processed shouldBe List(
        """{"clientId":"ClientB","amount":"2"}"""
      )
    }
  }

  test("enrich transaction events with client data")  { topicPrefix =>
    val in = topicPrefix + "in"
    val out = topicPrefix + "out"

    val process = EspProcessBuilder
      .id("example2")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-transaction", "topic" -> s"'$in'")
      .enricher("clientEnricher", "client", "clientService", "clientId" -> "#input.clientId")
      .sink("end",
        "#UTIL.mapToJson({'clientId': #input.clientId, 'clientName': #client.name, 'cardNumber': #client.cardNumber})",
        "kafka-stringSink", "topic" -> s"'$out'"
      )

    //this event triggers clientEnricher exception which will be logged
    sendTransaction(in, Transaction("ClientX", 3))

    //these are happy path events
    sendTransaction(in, Transaction("Client1", 1))
    sendTransaction(in, Transaction("Client2", 2))

    register(process) {

      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(out).take(2).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
      processed.toSet shouldBe Set(
        """{"clientId":"Client1","clientName":"Alice","cardNumber":"123"}""",
        """{"clientId":"Client2","clientName":"Bob","cardNumber":"234"}"""
      )
    }
  }

  test("perform transaction amount aggregation for every client") { topicPrefix =>
    val in = topicPrefix + "in"
    val out = topicPrefix + "out"

    val process =EspProcessBuilder
        .id("example3")
        .parallelism(1)
        .exceptionHandler()
        .source("start", "kafka-transaction", "topic" -> s"'$in'")
        .customNode("aggregate", "aggregatedAmount", "transactionAmountAggregator", "clientId" -> "#input.clientId")
        .filter("aggregateFilter", "#aggregatedAmount.amount > 10")
        .sink("end",
          "#UTIL.mapToJson({'clientId': #input.clientId, 'aggregatedAmount': #aggregatedAmount.amount})",
          "kafka-stringSink", "topic" -> s"'$out'"
        )
    sendTransaction(in, Transaction("Client1", 2))
    sendTransaction(in, Transaction("Client1", 9))
    sendTransaction(in, Transaction("Client2", 1))
    sendTransaction(in, Transaction("Client2", 9))
    sendTransaction(in, Transaction("Client3", 13))

    register(process) {

      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(out).take(2).map(msg => new String(msg.message(), StandardCharsets.UTF_8)).toList
      processed.toSet shouldBe Set(
        """{"clientId":"Client1","aggregatedAmount":"11"}""",
        """{"clientId":"Client3","aggregatedAmount":"13"}"""
      )
    }
  }

  test("count transactions in 1h window") { topicPrefix =>
      val in = topicPrefix + "in"
      val out = topicPrefix + "out"

    val process = EspProcessBuilder
      .id("example4")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "kafka-transaction", "topic" -> s"'$in'")
      .customNode("transactionCounter", "transactionCounts", "eventsCounter", "key" -> "#input.clientId", "length" -> "'1h'")
      .filter("aggregateFilter", "#transactionCounts.count > 1")
      .sink("end",
        "#UTIL.mapToJson({'clientId': #input.clientId, 'transactionsCount': #transactionCounts.count})",
        "kafka-stringSink", "topic" -> s"'$out'"
      )

    val now = LocalDateTime.of(2017, 1, 1, 10, 0)
    val windowLengthMinutes = 60

    //transactions for clientId=Client1 are within window so will pass whole process
    sendTransaction(in, Transaction("Client1", 1, toEpoch(now)))
    sendTransaction(in, Transaction("Client1", 2, toEpoch(now.plusMinutes(windowLengthMinutes - 10))))

    //transactions for clientId=Client2 are NOT within window so will NOT pass whole process
    sendTransaction(in, Transaction("Client2", 1, toEpoch(now)))
    sendTransaction(in, Transaction("Client2", 2, toEpoch(now.plusMinutes(windowLengthMinutes + 2))))

    //transactions for clientId=Client3 are within window so will pass whole process
    sendTransaction(in, Transaction("Client3", 1, toEpoch(now.plusMinutes(72))))
    sendTransaction(in, Transaction("Client3", 2, toEpoch(now.plusMinutes(72 + windowLengthMinutes - 2))))

    register(process) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(out).take(2).toList.map(msg => new String(msg.message(), StandardCharsets.UTF_8))
      processed.toSet shouldBe Set(
        """{"clientId":"Client1","transactionsCount":"2"}""",
        """{"clientId":"Client3","transactionsCount":"2"}"""
      )
    }
  }

  def register(process: EspProcess)(action: => Unit):Unit= {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }

  def sendTransaction(topic: String, t: Transaction): Future[RecordMetadata] = {
    kafkaClient.sendMessage(topic, t.asJson.noSpaces)
  }

  def toEpoch(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

}

class ExampleItTestsJava extends ExampleItTests with BaseJavaITest
class ExampleItTestsScala extends ExampleItTests with BaseScalaITest