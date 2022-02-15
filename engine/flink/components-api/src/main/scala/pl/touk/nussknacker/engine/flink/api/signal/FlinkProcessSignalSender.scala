package pl.touk.nussknacker.engine.flink.api.signal

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.api.common.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender

trait FlinkProcessSignalSender extends ProcessSignalSender {

  def connectWithSignals[InputType, SignalType: TypeInformation](start: DataStream[InputType], processId: String,
                                                                 nodeId: String, schema: DeserializationSchema[SignalType]): ConnectedStreams[InputType, SignalType]

}
