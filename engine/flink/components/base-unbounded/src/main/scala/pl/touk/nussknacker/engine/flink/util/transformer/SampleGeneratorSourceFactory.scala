package pl.touk.nussknacker.engine.flink.util.transformer

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkContextInitializingFunction,
  FlinkCustomNodeContext,
  FlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

import java.time.Duration
import java.{util => jul}
import javax.annotation.Nullable
import javax.validation.constraints.Min
import scala.jdk.CollectionConverters._

// TODO: add testing capabilities
object SampleGeneratorSourceFactory
    extends SampleGeneratorSourceFactory(
      new StandardTimestampWatermarkHandler[AnyRef](
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(
            new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)
          )
      )
    )

class SampleGeneratorSourceFactory(timestampAssigner: TimestampWatermarkHandler[AnyRef])
    extends SourceFactory
    with UnboundedStreamComponent {

  import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits._

  @silent("deprecated")
  @MethodToInvoke
  def create(
      @ParamName("period") period: Duration,
      // TODO: @DefaultValue(1) instead of nullable
      @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
      @ParamName("value") value: LazyParameter[AnyRef]
  ): Source = {
    new FlinkSource with ReturningType {

      override def contextStream(env: StreamExecutionEnvironment, ctx: FlinkCustomNodeContext): DataStream[Context] = {
        val count       = Option(nullableCount).map(_.toInt).getOrElse(1)
        val processName = ctx.metaData.name
        val stream = env
          .addSource(new PeriodicFunction(period))
          .map(_ => Context(processName.value))
          .flatMap(new ContextMultiplierPerCountFunction(count))
          .flatMap(ctx.lazyParameterHelper.lazyMapFunction(value))
          .flatMap(
            (value: ValueWithContext[AnyRef], out: Collector[AnyRef]) => out.collect(value.value),
            TypeInformationDetection.instance.forType[AnyRef](value.returnType)
          )

        val rawSourceWithTimestamp = timestampAssigner.assignTimestampAndWatermarks(stream)

        rawSourceWithTimestamp
          .map(
            new FlinkContextInitializingFunction[AnyRef](
              new BasicContextInitializer[AnyRef](Unknown),
              ctx.nodeId,
              ctx.convertToEngineRuntimeContext
            ),
            ctx.contextTypeInfo
          )
      }

      override val returnType: typing.TypingResult = value.returnType

    }
  }

  class ContextMultiplierPerCountFunction(count: Int) extends FlatMapFunction[Context, Context] with Serializable {

    override def flatMap(context: Context, out: Collector[Context]): Unit = {
      1.to(count).foreach(_ => out.collect(context))
    }

  }

}

@silent("deprecated")
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
