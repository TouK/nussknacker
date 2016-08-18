package pl.touk.esp.engine.process

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.service.{Parameter, ServiceRef}
import pl.touk.esp.engine.graph.{EspProcess, source}
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.esp.engine.process.KeyValueTestHelper.MockService
import pl.touk.esp.engine.spel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

class FlinkProcessRegistrarKafkaSpec
  extends FlatSpec
    with BeforeAndAfterAll
    with KafkaSpec
    with Matchers
    with ScalaFutures
    with Eventually
    with LazyLogging {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  import spel.Implicits._

  it should "aggregate records with triggering" in {
    val id = "itest.agg." + UUID.randomUUID().toString
    val inTopic = id + ".in"
    kafkaClient.createTopic(inTopic, partitions = 5)

    val windowWidth = 1 second
    val slidesInWindow = 5
    val messagesInSlide = 2
    val threshold = slidesInWindow * messagesInSlide

    val process = EspProcess(
      MetaData("proc1"),
      GraphBuilder.source("source", "kafka-keyvalue", source.Parameter("topic", inTopic))
        .aggregate(
          id = "aggregate", aggregatedVar = "input", keyExpression = "#input.key",
          duration = windowWidth * slidesInWindow, step = windowWidth, triggerExpression = Some(s"#input == $threshold"), foldingFunRef = Some("sum")
        )
        .processorEnd("service", ServiceRef("mock", List(Parameter("input", "#input"))))
    )

    Future {
      KeyValueTestHelper.processInvoker.invokeWithKafka(
        process,
        KafkaConfig(kafkaZookeeperServer.zkAddress, kafkaZookeeperServer.kafkaAddress, None)
      )
    }

    val keys = 10
    val slides = 100
    val outputCount = keys * (slides - (slidesInWindow - 1))

    val messagesStream =
      for {
        slide <- (0 until slides).view
        messageInSlide <- 0 until messagesInSlide
        key <- 1 to keys
      } yield {
        val timestamp = slide * windowWidth.toMillis + messageInSlide
        (key.toString, s"$key|1|$timestamp")
      }
    messagesStream.foreach {
      case (key, content) =>
        kafkaClient.sendMessage(inTopic, key, content)
    }
    kafkaClient.flush()

    def checkResultIsCorrect() = {
      MockService.data should have length outputCount
      MockService.data.toArray() shouldEqual (1 to outputCount).map(_ => threshold).toArray
    }

    eventually {
      checkResultIsCorrect()
    }
  }

}