package pl.touk.nussknacker.engine.management

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

case class FlinkConfig(jobManagerTimeout: FiniteDuration,
                       queryableStateProxyUrl: Option[String],
                       restUrl: String,
                       shouldVerifyBeforeDeploy: Option[Boolean])
