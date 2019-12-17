package pl.touk.nussknacker.engine.process

import java.util.UUID

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions

class FlinkStreamingProcessRegistrarKafkaSpec
  extends FlatSpec
    with BeforeAndAfterAll
    with KafkaSpec
    with Matchers
    with VeryPatientScalaFutures
    with LazyLogging {

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
      MetaData("proc1", StreamMetaData()),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder.source("source", "kafka-keyvalue", "topic" -> inTopic)
        .processorEnd("service", "mock", "input" -> "#input"))
    )

    Future {
      ProcessTestHelpers.processInvoker.invokeWithKafka(
        process, KafkaConfig(kafkaZookeeperServer.kafkaAddress, None, None)
      )
    }

    val keys = 10
    val slides = 100
    val triggeredRatio = 0.01
    val outputCount = (keys * triggeredRatio).toInt * (slides - (slidesInWindow - 1))

    val messagesStream =
      for {
        slide <- (0 until slides).view
        messageInSlide <- 0 until messagesInSlide
        key <- 1 to keys
      } yield {
        val timestamp = slide * windowWidth.toMillis + messageInSlide
        val value = if (key.toDouble / keys <= triggeredRatio) "1" else "0"
        (key.toString, s"$key|$value|$timestamp")
      }
    messagesStream.foreach {
      case (key, content) =>
        kafkaClient.sendMessage(inTopic, key, content)
    }
    kafkaClient.flush()

    def checkResultIsCorrect() = {
      MockService.data should have length outputCount
      MockService.data shouldEqual (1 to outputCount).map(_ => threshold).toList
    }

    eventually {
      checkResultIsCorrect()
    }
  }

}