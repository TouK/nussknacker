package pl.touk.nussknacker.engine.flink.api.exception

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{Lifecycle, MetaData, NamedServiceProvider}
import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with Lifecycle

trait FlinkEspExceptionConsumerProvider extends NamedServiceProvider {

  def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer

}
