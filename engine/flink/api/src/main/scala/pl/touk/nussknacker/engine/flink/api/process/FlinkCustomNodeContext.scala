package pl.touk.nussknacker.engine.flink.api.process

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

case class FlinkCustomNodeContext(metaData: MetaData,
                                  // TODO: it can be used in state recovery - make sure that it won't change during renaming of nodes on gui
                                  nodeId: String,
                                  timeout: FiniteDuration,
                                  lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                  signalSenderProvider: FlinkProcessSignalSenderProvider,
                                  globalParameters: Option[NkGlobalParameters])

case class SignalSenderKey(id: String, klass: Class[_])

class FlinkProcessSignalSenderProvider(signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender]) {

  //TODO: maybe search by id from SignalSenderKey??
  def get[T<:FlinkProcessSignalSender](implicit tag: ClassTag[T]): FlinkProcessSignalSender = {
    val clazz = tag.runtimeClass
    signalSenders.find { case (SignalSenderKey(_, klass), _) => clazz.isAssignableFrom(klass) }
      .getOrElse(throw new IllegalArgumentException(s"Unknown signal class: ${clazz.getName}, please check ProcessConfigCreator"))._2
  }
}

case class FlinkProcessConfig()
