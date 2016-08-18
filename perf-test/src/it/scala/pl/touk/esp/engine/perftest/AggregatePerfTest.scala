package pl.touk.esp.engine.perftest

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import org.scalatest.concurrent.Eventually
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.{EspProcess, sink, source}
import pl.touk.esp.engine.kafka.{KafkaClient, KafkaConfig}
import pl.touk.esp.engine.perftest.AggregatePerfTest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class AggregatePerfTest extends FlatSpec with BasePerfTest with Matchers  with Eventually with LazyLogging {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.esp.engine.kafka.KafkaUtils._

  override protected def baseTestName = "agg"

  override protected def configCreatorClassName = "AggProcessConfigCreator"

  "simple process" should "has low memory footprint" in {
    val inTopic = processId + ".in"
    val outTopic = processId + ".out"
    val kafkaClient = prepareKafka(config, inTopic, outTopic)
    try {
      val outputStream = kafkaClient.createConsumer().consume(outTopic)

      val windowWidth = 1 second
      val slidesInWindow = 5
      val messagesInSlide = 10
      val threshold = slidesInWindow * messagesInSlide

      val process = prepareSimpleProcess(
        id = processId,
        inTopic = inTopic,
        outTopic = outTopic,
        slideWidth = windowWidth,
        slidesInWindow = slidesInWindow,
        threshold = threshold)

      withDeployedProcess(process) {
        val keys = 100
        val slides = 1000
        val inputCount = keys * slides * messagesInSlide

        val groupSize = 100 * 1000
        val groupsCount = Math.ceil(inputCount.toDouble / groupSize).toInt

        val outputCount = keys * (slides - (slidesInWindow - 1))

        val messagesStream =
          for {
            slide <- (0 until slides).view
            messageInSlide <- 0 until messagesInSlide
            keyIdx <- 1 to keys
          } yield {
            val timestamp = slide * windowWidth.toMillis + messageInSlide
            val key = s"key$keyIdx"
            (key, s"$key|1|$timestamp")
          }

        val (lastMessage, metrics, durationInMillis) = collectMetricsIn {
          for {
            t <- messagesStream.grouped(groupSize).zipWithIndex
            (group, idx) = t
          } {
            Future.sequence(group.map {
              case (key, content) =>
                kafkaClient.sendMessage(inTopic, key, content)
            }.force).map { _ ->
              logger.info(s"Group ${idx + 1} / $groupsCount sent")
            }
          }
          kafkaClient.producer.flush()

          outputStream
            .takeNthNonBlocking(outputCount)
            .futureValue(10 minutes)
        }

        logger.info(metrics.show)
        val usedMemoryInMB = metrics.memoryHistogram.percentile(95.0) / 1000 / 1000
        val msgsPerSecond = outputCount.toDouble / (durationInMillis.toDouble / 1000)
        logger.info(f"Throughput: $msgsPerSecond%.2f msgs/sec.")

        usedMemoryInMB should be < 500L
        msgsPerSecond should be > 1000.0
      }

    } finally {
      clearKafka(kafkaClient, inTopic, outTopic)
    }
  }

  private def prepareKafka(config: Config, inTopic: String, outTopic: String): KafkaClient = {
    val kafkaConfig = config.as[KafkaConfig](s"$profile.kafka")
    val kafkaClient = new KafkaClient(kafkaConfig.kafkaAddress, kafkaConfig.zkAddress)
    // FIXME: przy większej ilości partycji w logach taskmanagera pojawia się Timestamp monotony violated i test nie przechodzi
    kafkaClient.createTopic(inTopic, partitions = 1)
    kafkaClient.createTopic(outTopic, partitions = 1)
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
                           outTopic: String,
                           slideWidth: FiniteDuration,
                           slidesInWindow: Int,
                           threshold: Int) = {
    val graph =
      GraphBuilder.source("source", "kafka-keyvalue",
        source.Parameter("topic", inTopic)
      )
      .aggregate(
        id = "aggregate", aggregatedVar = "input", keyExpression = "#input.key",
        duration = slideWidth * slidesInWindow, step = slideWidth, foldingFunRef = Some("sum"), triggerExpression = Some(s"#input == $threshold")
      )
      .sink("sink", "#input", "kafka-int",
        sink.Parameter("topic", outTopic)
      )

    EspProcess(
      MetaData(id),
      graph
    )
  }

}