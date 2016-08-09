package pl.touk.esp.engine.perftest

import com.typesafe.config.Config
import org.scalatest._
import org.scalatest.concurrent.Eventually
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.{EspProcess, sink, source}
import pl.touk.esp.engine.kafka.KafkaClient
import pl.touk.esp.engine.perftest.AggregatePerfTest._
import pl.touk.esp.engine.perftest.sample.AggProcessConfigCreator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.esp.engine.kafka.KafkaConfig

class AggregatePerfTest extends FlatSpec with BasePerfTest with Matchers with Eventually {

  import pl.touk.esp.engine.kafka.KafkaUtils._

  override protected def baseTestName: String = "agg"

  override protected def configCreatorClass: Class[_] = classOf[AggProcessConfigCreator]

  "simple process" should "has low memory footprint" in {
    val inTopic = processId + ".in"
    val outTopic = processId + ".out"
    val kafkaClient = prepareKafka(config, inTopic, outTopic)
    try {
      val consumer = kafkaClient.createConsumer()
      kafkaClient.sendMessage(inTopic, "key1|1|0")

      deployProcess(prepareSimpleProcess(processId, inTopic, outTopic))

      try {
        val sum = consumer
          .consume(outTopic)
          .takeNonBlocking(1)
          .map(_.head.message())
          .map(new String(_))
          .map(_.toLong)
          .futureValue

        sum shouldEqual 1L
      } finally {
        cancelProcess()
      }

    } finally {
      clearKafka(kafkaClient, inTopic, outTopic)
    }
  }

  private def prepareKafka(config: Config, inTopic: String, outTopic: String): KafkaClient = {
    val kafkaConfig = config.as[KafkaConfig](s"$profile.kafka")
    val kafkaClient = new KafkaClient(kafkaConfig.kafkaAddress, kafkaConfig.zkAddress)
    kafkaClient.createTopic(inTopic)
    kafkaClient.createTopic(outTopic)
    kafkaClient
  }

  private def clearKafka(kafkaClient: KafkaClient, inTopic: String, outTopic: String) = {
    kafkaClient.deleteTopic(inTopic)
    kafkaClient.deleteTopic(outTopic)
    kafkaClient.shutdown()
  }

}

object AggregatePerfTest {
  import pl.touk.esp.engine.spel.Implicits._

  def prepareSimpleProcess(id: String,
                           inTopic: String,
                           outTopic: String) = {
    val graph =
      GraphBuilder.source("source", "kafka-keyvalue",
        source.Parameter("topic", inTopic)
      )
      .aggregate(
        id = "aggregate", aggregatedVar = "input", keyExpression = "#input.key",
        duration = 5 seconds, step = 1 second
      )
      .sink("sink", "#sum(#input.![value])", "kafka-long",
        sink.Parameter("topic", outTopic)
      )

    EspProcess(
      MetaData(id),
      graph
    )
  }

}