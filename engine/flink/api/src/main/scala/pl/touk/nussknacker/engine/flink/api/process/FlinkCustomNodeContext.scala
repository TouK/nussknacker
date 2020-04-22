package pl.touk.nussknacker.engine.flink.api.process

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

case class FlinkCustomNodeContext(metaData: MetaData,
                                  nodeId: String,
                                  timeout: FiniteDuration,
                                  lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                  signalSenderProvider: FlinkProcessSignalSenderProvider,
                                  validationContext: Either[ValidationContext, Map[String, ValidationContext]]
                                 )

case class SignalSenderKey(id: String, klass: Class[_])

class FlinkProcessSignalSenderProvider(signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender]) {

  //TODO: maybe search by id from SignalSenderKey??
  def get[T<:FlinkProcessSignalSender](implicit tag: ClassTag[T]): FlinkProcessSignalSender = {
    val clazz = tag.runtimeClass
    signalSenders.find { case (SignalSenderKey(_, klass), _) => clazz.isAssignableFrom(klass) }
      .getOrElse(throw new IllegalArgumentException(s"Unknown signal class: ${clazz.getName}, please check ProcessConfigCreator"))._2
  }
}
