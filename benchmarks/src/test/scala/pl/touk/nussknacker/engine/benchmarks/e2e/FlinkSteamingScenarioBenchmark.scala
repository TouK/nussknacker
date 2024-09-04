package pl.touk.nussknacker.engine.benchmarks.e2e

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging

import java.time.{Duration, Instant}
import scala.util.Random

object FlinkSteamingScenarioBenchmark extends IOApp with BaseE2EBenchmark with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      messagesCount <- readBenchmarkMessagesCount(args)
      _             <- log("Starting message generation...")
      _             <- generateBenchmarkMessages(messagesCount)
      _             <- log("Generation finished!")
      _             <- log("Starting benchmark scenario...")
      _             <- runScenarioAndStartProcessing()
      _             <- log("Scenario started. Messages are processing...")
      time          <- measureProcessingTime()
      _             <- log(s"All messages processed. It took ${time.toSeconds} seconds")
    } yield ExitCode.Success
  }

  private def readBenchmarkMessagesCount(args: List[String]) = IO.delay {
    args.headOption match {
      case Some(arg) =>
        arg.toIntOption match {
          case Some(count) => count
          case None => throw new IllegalArgumentException("Invalid number format. Please provide a valid integer.")
        }
      case None =>
        throw new IllegalArgumentException("No arguments provided. Please provide an integer argument.")
    }
  }

  private def generateBenchmarkMessages(messagesCount: Int) = IO.delay {
    val batch       = 2000
    val noOfBatches = Math.ceil(messagesCount / batch).toInt
    (1 to noOfBatches)
      .foreach { batchId =>
        if (batchId % 10 == 0) logger.info(s"Generated ${"%.2f".format(batchId * 100.0 / noOfBatches)}% of messages...")
        sendTestMessages(batchId, batch)
      }
    sendMessageToKafka("transactions", generateMessage(s"${messagesCount + 1}", last = true))
  }

  private def runScenarioAndStartProcessing() = IO.delay {
    deployAndWaitForRunningState("DetectLargeTransactions")
  }

  private def measureProcessingTime() = IO.delay {
    def foundLastRequest = readAllMessagesFromKafka("alerts")
      .exists(json => json.obj.get("message").exists(_.str == "Last request"))

    val started = Instant.now()

    while (!foundLastRequest) {
      Thread.sleep(100)
    }

    Duration.between(started, Instant.now())
  }

  private def sendTestMessages(batchId: Int, batchSize: Int): Unit = {
    sendMessagesToKafka(
      "transactions",
      (0 until batchSize).map { id => generateMessage(s"${batchId + id}", last = false) }
    )
  }

  private def generateMessage(id: String, last: Boolean) = ujson.Obj(
    "amount"   -> (Random.nextInt(1000) + 1),
    "clientId" -> s"$id",
    "isLast"   -> last
  )

  private def log(message: => String) = IO.delay(logger.info(message))
}
