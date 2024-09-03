package pl.touk.nussknacker.engine.benchmarks.e2e

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging

import java.time.{Duration, Instant}

object Example extends IOApp with BaseE2EBenchmark with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] =
    IO
      .delay {
        val max = 100000
        (1 to max).foreach(_ => sendTestMessages(100))
        sendTestMessages(1, last = true)

        deployAndWaitForRunningState("DetectLargeTransactions")

        waitForLastMessage()

        logger.info("DONE")
      }
      .map(_ => ExitCode.Success)

  private def sendTestMessages(count: Int, last: Boolean = false): Unit = {
    sendMessagesToKafka(
      "transactions",
      (1 to count).map { id =>
        ujson.Obj(
          "amount"   -> 100,
          "clientId" -> s"$id",
          "isLast"   -> last
        )
      }
    )
  }

  private def waitForLastMessage(): Unit = {
    def foundLastRequest = readAllMessagesFromKafka("alerts")
      .exists(json => json.obj.get("message").exists(_.str == "Last request"))

    val started = Instant.now()

    while (!foundLastRequest) {
      Thread.sleep(100)
    }

    val duration = Duration.between(started, Instant.now())
    logger.info(s"Took: ${duration.toSeconds} sec")
  }

}
