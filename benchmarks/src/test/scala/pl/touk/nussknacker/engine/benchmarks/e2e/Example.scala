package pl.touk.nussknacker.engine.benchmarks.e2e

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.LazyLogging

object Example extends IOApp with BaseE2EBenchmark with LazyLogging {

  override def run(args: List[String]): IO[ExitCode] =
    IO
      .delay {
        logger.info("test")
      }
      .map(_ => ExitCode.Success)

}
