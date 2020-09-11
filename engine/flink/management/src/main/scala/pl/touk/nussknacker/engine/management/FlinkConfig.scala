package pl.touk.nussknacker.engine.management

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

case class FlinkConfig(jobManagerTimeout: FiniteDuration,
                       configLocation: Option[String],
                       customConfig: Option[Config],
                       queryableStateProxyUrl: Option[String],
                       restUrl: String,
                       shouldVerifyBeforeDeploy: Option[Boolean])
