package pl.touk.nussknacker.engine.management

import scala.concurrent.duration.FiniteDuration

case class FlinkConfig(jobManagerTimeout: Option[FiniteDuration],
                       queryableStateProxyUrl: Option[String],
                       restUrl: String,
                       shouldVerifyBeforeDeploy: Option[Boolean])
