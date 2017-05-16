package pl.touk.esp.engine.management.sample.signal

import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.operators.{AbstractStreamOperator, TwoInputStreamOperator}
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord
import pl.touk.esp.engine.api.signal.SignalTransformer
import pl.touk.esp.engine.api.signal.SignalTransformer._
import pl.touk.esp.engine.api.{MethodToInvoke, ParamName, _}
import pl.touk.esp.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.esp.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.esp.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.esp.engine.kafka.{EspSimpleKafkaProducer, KafkaConfig}


class RemoveLockProcessSignalFactory(val kafkaConfig: KafkaConfig, val signalsTopic: String)
  extends FlinkProcessSignalSender with EspSimpleKafkaProducer with KafkaSignalStreamConnector {

  import Signals._

  @MethodToInvoke
  def sendSignal(@ParamName("lockId") lockId: String)(processId: String) = {
    val signal = SampleProcessSignal(processId, System.currentTimeMillis(), RemoveLock(lockId))
    val json = ProcessSignalCodecs.processSignalCodec.Encoder(signal).nospaces
    sendToKafkaWithNewProducer(signalsTopic, Array.empty, json.getBytes)
  }

}

object SampleSignalHandlingTransformer {
  import Signals._

  trait SignalHandler extends LazyLogging {
    val metaData: MetaData

    def handleIfSignalForThisProcess(signal: SampleProcessSignal)(handle: SampleProcessSignal => Unit): Unit = {
      if (metaData.id == signal.processId) {
        logger.info(s"Signal received: $signal")
        handle(signal)
      } else {
        logger.debug(s"Signal for other process received, ignoring. Current process ${metaData.id}, signal $signal")
      }
    }
  }

  class LockStreamTransformer extends CustomStreamTransformer {

    @SignalTransformer(signalClass = classOf[RemoveLockProcessSignalFactory])
    @MethodToInvoke(returnType = classOf[LockOutput])
    def execute(@ParamName("input") input: LazyInterpreter[String]) =
      FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], context: FlinkCustomNodeContext) => {
        context.signalSenderProvider.get[RemoveLockProcessSignalFactory].connectWithSignals(start, context.metaData.id, context.nodeId, SignalSchema.deserializationSchema)
          .keyBy(_ => 1, _ => 1)
          .transform("lockStreamTransform", new LockStreamFunction(context.metaData))
      })
  }

  class LockStreamFunction(val metaData: MetaData)
    extends AbstractStreamOperator[ValueWithContext[Any]] with TwoInputStreamOperator[InterpretationResult, SampleProcessSignal, ValueWithContext[Any]]
      with LazyLogging with SignalHandler {

    var lockEnabledState: ValueState[java.lang.Boolean] = _

    override def open(): Unit = {
      super.open()
      lockEnabledState = getRuntimeContext.getState(new ValueStateDescriptor[java.lang.Boolean]("lockEnabled", classOf[java.lang.Boolean]))
    }

    override def processElement1(element: StreamRecord[InterpretationResult]): Unit = {
      setInitialStateIfStateNotDefined()
      output.collect(new StreamRecord[ValueWithContext[Any]](ValueWithContext(LockOutput(lockEnabledState.value()), element.getValue.finalContext)))
    }

    override def processElement2(element: StreamRecord[SampleProcessSignal]): Unit = {
      handleIfSignalForThisProcess(element.getValue) { signal =>
        signal.action match {
          case _: RemoveLock =>
            lockEnabledState.update(false)
            logger.info(s"Lock successfully removed $signal")
        }
      }
    }

    private def setInitialStateIfStateNotDefined() = {
      if (lockEnabledState.value() == null) {
        lockEnabledState.update(true)
      }
    }
  }

  case class LockOutput(lockEnabled: Boolean)
}