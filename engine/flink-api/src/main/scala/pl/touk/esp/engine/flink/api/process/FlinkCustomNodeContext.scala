package pl.touk.esp.engine.flink.api.process

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.flink.api.signal.FlinkProcessSignalSender

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

case class FlinkCustomNodeContext(metaData: MetaData,
                                  nodeId: String,
                                  timeout: FiniteDuration,
                                  exceptionHandler: ()=>FlinkEspExceptionHandler,
                                  signalSenderProvider: FlinkProcessSignalSenderProvider)

case class SignalSenderKey(id: String, klass: Class[_])

class FlinkProcessSignalSenderProvider(signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender]) {

  //TODO: a nazwa??
  def get[T<:FlinkProcessSignalSender](implicit tag: ClassTag[T]): FlinkProcessSignalSender = {
    val clazz = tag.runtimeClass
    signalSenders.find { case (SignalSenderKey(_, klass), _) => clazz.isAssignableFrom(klass) }
      .getOrElse(throw new IllegalArgumentException(s"Unknown signal class: ${clazz.getName}, please check ProcessConfigCreator"))._2
  }
}
