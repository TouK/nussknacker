package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializingFunction, FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

import java.time.Duration
import java.{util => jul}
import javax.annotation.Nullable
import javax.validation.constraints.Min
import scala.jdk.CollectionConverters._

// TODO: add testing capabilities
object PeriodicSourceFactory
    extends PeriodicSourceFactory(
      new StandardTimestampWatermarkHandler[AnyRef](
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(
            new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)
          )
      )
    )

class PeriodicSourceFactory(timestampAssigner: TimestampWatermarkHandler[AnyRef])
    extends SourceFactory
    with UnboundedStreamComponent {

  @MethodToInvoke
  def create(
      @ParamName("period") period: Duration,
      // TODO: @DefaultValue(1) instead of nullable
      @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
      @ParamName("value") value: LazyParameter[AnyRef]
  ): Source = {
    new FlinkSource with ReturningType {

      override def sourceStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[Context] = {

        val count       = Option(nullableCount).map(_.toInt).getOrElse(1)
        val processName = flinkNodeContext.metaData.name
        val stream = env
          .addSource(new PeriodicFunction(period))
          .map(_ => Context(processName.value))
          .flatMap(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap { (v: ValueWithContext[AnyRef], c: Collector[AnyRef]) =>
            1.to(count).map(_ => v.value).foreach(c.collect)
          }
          .returns(TypeInformation.of(classOf[AnyRef]))

        val rawSourceWithTimestamp = timestampAssigner.assignTimestampAndWatermarks(stream)

        rawSourceWithTimestamp
          .map(
            new FlinkContextInitializingFunction[AnyRef](
              new BasicContextInitializer[AnyRef](Unknown),
              flinkNodeContext.nodeId,
              flinkNodeContext.convertToEngineRuntimeContext
            ),
            flinkNodeContext.contextTypeInfo
          )
      }

      override val returnType: typing.TypingResult = value.returnType

    }
  }

}

class PeriodicFunction(period: Duration) extends SourceFunction[Unit] {

  @volatile private var isRunning = true

  override def run(ctx: SourceFunction.SourceContext[Unit]): Unit = {
    while (isRunning) {
      ctx.collect(())
      Thread.sleep(period.toMillis)
    }
  }

  override def cancel(): Unit = {
    isRunning = false
  }

}

class MapAscendingTimestampExtractor(timestampField: String) extends SerializableTimestampAssigner[AnyRef] {

  override def extractTimestamp(element: scala.AnyRef, recordTimestamp: Long): Long = {
    element match {
      case m: jul.Map[String @unchecked, AnyRef @unchecked] =>
        m.asScala
          .get(timestampField)
          .map(value => supportedTypeToMillis(value, timestampField))
          .getOrElse(System.currentTimeMillis())
      case _ =>
        System.currentTimeMillis()
    }
  }

}

object MapAscendingTimestampExtractor {
  val DefaultTimestampField = "timestamp"
}
