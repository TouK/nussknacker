package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.ScenarioProcessingContext
import pl.touk.nussknacker.engine.api.process.{Source, SourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

/**
  * Source with methods specific for Flink
  */
trait FlinkSource extends Source {

  def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ScenarioProcessingContext]

}

/**
  * Support for test mechanism for typical flink sources.
  *
  * @tparam Raw - type of raw event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
trait FlinkSourceTestSupport[Raw] extends SourceTestSupport[Raw] { self: Source with FlinkIntermediateRawSource[Raw] =>

  // TODO: design better way of handling test data in generic FlinkSource
  // Probably we *still* want to use CollectionSource (and have some custom logic in parser if needed), but timestamps
  // have to be handled here for now
  def timestampAssignerForTest: Option[TimestampWatermarkHandler[Raw]]

  def typeInformation: TypeInformation[Raw]

}

/**
  * Typical source with methods specific for Flink, user has only to define Source function, type information and timestamp assigner.
  * Optionally [[FlinkSourceTestSupport]] can be mixed in to provide generating test data while authoring scenarios.
  *
  * @tparam Raw - type of raw event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
trait BasicFlinkSource[Raw] extends FlinkSource with FlinkIntermediateRawSource[Raw] {

  def flinkSourceFunction: SourceFunction[Raw]

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ScenarioProcessingContext] = {
    prepareSourceStream(env, flinkNodeContext, flinkSourceFunction)
  }

}
