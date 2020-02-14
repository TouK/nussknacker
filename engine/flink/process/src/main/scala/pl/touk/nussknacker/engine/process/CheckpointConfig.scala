package pl.touk.nussknacker.engine.process

import scala.concurrent.duration.FiniteDuration

case class CheckpointConfig(checkpointInterval: FiniteDuration,
                            minPauseBetweenCheckpoints: Option[FiniteDuration],
                            maxConcurrentCheckpoints: Option[Int],
                            tolerableCheckpointFailureNumber: Option[Int]
                           )
