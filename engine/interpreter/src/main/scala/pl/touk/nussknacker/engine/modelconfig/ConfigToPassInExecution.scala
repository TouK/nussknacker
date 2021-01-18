package pl.touk.nussknacker.engine.modelconfig

import com.typesafe.config.Config

trait ConfigToPassInExecution {
  def transform(config: Config): Config
}
