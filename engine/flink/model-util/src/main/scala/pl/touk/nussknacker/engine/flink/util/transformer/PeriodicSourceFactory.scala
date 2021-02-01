package pl.touk.nussknacker.engine.flink.util.transformer

import java.time.Duration
import java.{util => jul}

import com.github.ghik.silencer.silent
import javax.annotation.Nullable
import javax.validation.constraints.Min
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource, FlinkSourceFactory, SourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{LegacyTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.context.InitContextFunction

import scala.annotation.nowarn
import scala.collection.JavaConverters._

// TODO: add testing capabilities
object PeriodicSourceFactory extends PeriodicSourceFactory(
  new LegacyTimestampWatermarkHandler(new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)))

class PeriodicSourceFactory(timestampAssigner: TimestampWatermarkHandler[AnyRef]) extends FlinkSourceFactory[AnyRef]  {

  @MethodToInvoke
  def create(@ParamName("period") period: Duration,
             // TODO: @DefaultValue(1) instead of nullable
             @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
             @ParamName("value") value: LazyParameter[AnyRef]): Source[_] = {
    new FlinkSource[AnyRef] with ReturningType with SourceTestSupport[AnyRef] {

      override def typeInformation: TypeInformation[AnyRef] = implicitly[TypeInformation[AnyRef]]

      override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

        val count = Option(nullableCount).map(_.toInt).getOrElse(1)
        val processId = flinkNodeContext.metaData.id
        val stream = env
          .addSource(new PeriodicFunction(period))
          .map(_ => Context(processId))
          .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap { v =>
            1.to(count).map(_ => v.value)
          }

        val rawSourceWithTimestamp = timestampAssigner.assignTimestampAndWatermarks(stream)

        rawSourceWithTimestamp
          .map(new InitContextFunction[AnyRef](flinkNodeContext.metaData.id, flinkNodeContext.nodeId, None))(flinkNodeContext.contextTypeInformation.left.get)
      }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[AnyRef]] = Some(timestampAssigner)

      override val returnType: typing.TypingResult = value.returnType

    }
  }

}

class PeriodicFunction(duration: Duration) extends SourceFunction[Unit] {

  @volatile private var isRunning = true

  override def run(ctx: SourceFunction.SourceContext[Unit]): Unit = {
    while (isRunning) {
      ctx.collect(Unit)
      Thread.sleep(duration.toMillis)
    }
  }

  override def cancel(): Unit = {
    isRunning = false
  }

}

@silent("deprecated")
@nowarn("deprecated")
class MapAscendingTimestampExtractor(timestampField: String) extends AscendingTimestampExtractor[AnyRef] {
  override def extractAscendingTimestamp(element: AnyRef): Long = {
    element match {
      case m: jul.Map[String@unchecked, AnyRef@unchecked] =>
        m.asScala
          .get(timestampField).map(_.asInstanceOf[Long])
          .getOrElse(System.currentTimeMillis())
      case _ =>
        System.currentTimeMillis()
    }
  }
}

object MapAscendingTimestampExtractor {
  val DefaultTimestampField = "timestamp"
}