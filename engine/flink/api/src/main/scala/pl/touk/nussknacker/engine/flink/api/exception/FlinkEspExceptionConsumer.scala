package pl.touk.nussknacker.engine.flink.api.exception

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MetaData, NamedServiceProvider}
import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with RuntimeContextLifecycle {

  def close() : Unit = {}

}

trait FlinkEspExceptionConsumerProvider extends NamedServiceProvider {

  def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer

}