package pl.touk.nussknacker.engine.flink.api.process.batch

import org.apache.flink.api.common.io.OutputFormat
import pl.touk.nussknacker.engine.api.process.Sink

trait FlinkBatchSink extends Sink {

  def toFlink: OutputFormat[Any]
}
