package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{Context, JobData, MetaData}
import pl.touk.nussknacker.engine.flink.api.{NkGlobalParameters}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

case class FlinkCustomNodeContext(jobData: JobData,
                                  // TODO: it can be used in state recovery - make sure that it won't change during renaming of nodes on gui
                                  nodeId: String,
                                  timeout: FiniteDuration,
                                  lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                  signalSenderProvider: FlinkProcessSignalSenderProvider,
                                  exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler,
                                  globalParameters: Option[NkGlobalParameters],
                                  validationContext: Either[ValidationContext, Map[String, ValidationContext]],
                                  contextTypeInformation: Either[TypeInformation[Context], Map[String, TypeInformation[Context]]]) {
  def metaData: MetaData = jobData.metaData
}

case class SignalSenderKey(id: String, klass: Class[_])

class FlinkProcessSignalSenderProvider(signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender]) {

  //TODO: maybe search by id from SignalSenderKey??
  def get[T<:FlinkProcessSignalSender](implicit tag: ClassTag[T]): FlinkProcessSignalSender = {
    val clazz = tag.runtimeClass
    signalSenders.find { case (SignalSenderKey(_, klass), _) => clazz.isAssignableFrom(klass) }
      .getOrElse(throw new IllegalArgumentException(s"Unknown signal class: ${clazz.getName}, please check ProcessConfigCreator"))._2
  }
}
