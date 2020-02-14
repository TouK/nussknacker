package pl.touk.nussknacker.engine.process

import scala.concurrent.duration.FiniteDuration

case class CheckpointConfig(checkpointInterval: FiniteDuration,
                            minPauseBetweenCheckpoints: Option[FiniteDuration] = None,
                            maxConcurrentCheckpoints: Option[Int] = None,
                            tolerableCheckpointFailureNumber: Option[Int] = None
                           )
