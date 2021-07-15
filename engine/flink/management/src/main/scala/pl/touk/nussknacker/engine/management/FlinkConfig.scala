package pl.touk.nussknacker.engine.management

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

case class FlinkConfig(restUrl: String,
                       queryableStateProxyUrl: Option[String],
                       jobManagerTimeout: FiniteDuration = 1 minute,
                       shouldVerifyBeforeDeploy: Boolean = true)
