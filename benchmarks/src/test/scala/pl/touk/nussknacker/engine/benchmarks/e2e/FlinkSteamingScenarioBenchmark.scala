package pl.touk.nussknacker.engine.benchmarks.e2e

import better.files.File.root
import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging

import java.time.{Duration, Instant}
import scala.concurrent.duration._
import scala.util.Random

object FlinkSteamingScenarioBenchmark extends IOApp with BaseE2EBenchmark with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      messagesCount        <- readBenchmarkMessagesCount(args)
      _                    <- log("Starting message generation...")
      prepareDataDuration  <- measure(generateBenchmarkMessages(messagesCount))
      _                    <- log("Generation finished!")
      _                    <- log("Starting benchmark scenario...")
      runScenarioDuration  <- measure(runScenarioAndStartProcessing())
      _                    <- log("Scenario started. Messages are processing...")
      verifyResultDuration <- measure(waitForProcessingFinish())
      _                    <- log(s"All messages processed.")
      _                    <- saveMeasurementsToFile(prepareDataDuration, runScenarioDuration, verifyResultDuration)
      _                    <- log(s"Results:")
      _                    <- log(s"- Preparing data: ${prepareDataDuration.duration.toSeconds} seconds")
      _                    <- log(s"- Running scenario: ${runScenarioDuration.duration.toSeconds} seconds")
      _                    <- log(s"- Verifying result: ${verifyResultDuration.duration.toSeconds} seconds")
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

  private def waitForProcessingFinish(): IO[Unit] = {
    def foundLastRequest(): IO[Boolean] = IO.delay {
      readAllMessagesFromKafka("alerts")
        .exists(json => json.obj.get("message").exists(_.str == "Last request"))
    }

    foundLastRequest()
      .flatMap {
        case false =>
          IO
            .sleep(100 millis)
            .flatMap(_ => waitForProcessingFinish())
        case true =>
          IO.pure(())
      }
  }

  private def saveMeasurementsToFile(
      prepareData: Measured[_],
      runScenario: Measured[_],
      verifyResult: Measured[_]
  ): IO[Unit] = IO.delay {
    def format(measured: Measured[_]) = "%.2f".format(measured.duration.toMillis.toDouble)

    (root / "tmp" / "benchmarkResult.csv")
      .createFileIfNotExists()
      .clear()
      .appendLine(s"prepareData,${format(prepareData)}")
      .appendLine(s"runScenario,${format(runScenario)}")
      .appendLine(s"verifyResult,${format(verifyResult)}")
      .appendLine()
  }

  private def measure[T](action: IO[T]): IO[Measured[T]] = {
    for {
      started  <- IO.delay(Instant.now())
      result   <- action
      duration <- IO.delay(Duration.between(started, Instant.now()))
    } yield Measured(result, duration)
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

final case class Measured[T](result: T, duration: Duration)
