package pl.touk.nussknacker.engine.flink.api.process

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer, Source, SourceTestSupport}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

/**
  * Source with methods specific for Flink
  */
trait FlinkSource extends Source {

  def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context]

}

/**
  * Support for test mechanism for typical flink sources.
  *
  * @tparam Raw - type of raw event that is generated by flink source function.
  *             This is needed to handle e.g. syntax suggestions in UI (in sources with explicite @MethodToInvoke).
  */
trait FlinkSourceTestSupport[Raw] extends SourceTestSupport[Raw] { self: Source =>

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
trait BasicFlinkSource[Raw]
    extends FlinkSource
    with CustomizableContextInitializerSource[Raw]
    with CustomizableTimestampWatermarkHandlerSource[Raw]
    with ExplicitTypeInformationSource[Raw] {

  @silent("deprecated")
  def flinkSourceFunction: SourceFunction[Raw]

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val source = FlinkStandardSourceUtils.createSource(env, flinkSourceFunction, typeInformation)
    FlinkStandardSourceUtils.prepareSource(source, flinkNodeContext, timestampAssigner, contextInitializer)
  }

}

/**
 * Source providing default logic for transforming a `DataStreamSource[Raw]` into `DataStream[Context]`. Fulfills
 * the same role as `BasicFlinkSource`, but is based on `DataStreamSource` instead of `SourceFunction`.
 * 
 * @tparam Raw - type of raw event that is generated by flink source function.
 */
// TODO local: better name?
trait StandardFlinkSource[Raw]
    extends FlinkSource
    with CustomizableContextInitializerSource[Raw]
    with CustomizableTimestampWatermarkHandlerSource[Raw] {

  def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[Raw]

  override final def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    FlinkStandardSourceUtils.prepareSource(
      source = initialSourceStream(env, flinkNodeContext),
      flinkNodeContext = flinkNodeContext,
      timestampAssigner = timestampAssigner,
      contextInitializer = contextInitializer
    )
  }

}

trait CustomizableContextInitializerSource[T] { self: Source =>
  def contextInitializer: ContextInitializer[T] = new BasicContextInitializer[T](Unknown)
}

trait CustomizableTimestampWatermarkHandlerSource[T] { self: Source =>
  def timestampAssigner: Option[TimestampWatermarkHandler[T]]
}

trait ExplicitTypeInformationSource[T] { self: Source =>
  def typeInformation: TypeInformation[T]
}
