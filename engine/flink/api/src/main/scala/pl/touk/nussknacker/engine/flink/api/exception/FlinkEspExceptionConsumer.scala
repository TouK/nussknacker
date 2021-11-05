package pl.touk.nussknacker.engine.flink.api.exception

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer
import pl.touk.nussknacker.engine.api.{Lifecycle, MetaData, NamedServiceProvider}

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with Lifecycle

trait FlinkEspExceptionConsumerProvider extends NamedServiceProvider {

  def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer

}
