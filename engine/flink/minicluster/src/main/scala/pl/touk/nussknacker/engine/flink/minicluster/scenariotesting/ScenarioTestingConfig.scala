package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class ScenarioTestingConfig(
    // it shouldn't be longer than pekko.http.server.request-timeout because we want to return inner timeout
    timeout: FiniteDuration = 1.minute - 5.seconds,
    parallelism: Int = 1
)
