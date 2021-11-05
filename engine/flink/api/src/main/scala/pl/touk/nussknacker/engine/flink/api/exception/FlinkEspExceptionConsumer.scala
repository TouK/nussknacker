package pl.touk.nussknacker.engine.flink.api.exception

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{JobData, Lifecycle, MetaData, NamedServiceProvider}
import pl.touk.nussknacker.engine.api.exception.EspExceptionConsumer
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle

trait FlinkEspExceptionConsumer extends EspExceptionConsumer with EngineRuntimeContextLifecycle with Lifecycle {
  override def open(context: EngineRuntimeContext): Unit = {}
}

trait FlinkEspExceptionConsumerProvider extends NamedServiceProvider {

  def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer

}
