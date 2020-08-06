package pl.touk.nussknacker.engine.flink.util.transformer

import java.time.Duration
import java.{util => jul}

import javax.annotation.Nullable
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource, FlinkSourceFactory}

import scala.collection.JavaConverters._

// TODO: add testing capabilities
object PeriodicSourceFactory extends PeriodicSourceFactory(new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField))

class PeriodicSourceFactory(timestampAssigner: TimestampAssigner[jul.Map[String, AnyRef]]) extends FlinkSourceFactory[jul.Map[String, AnyRef]]  {

  @MethodToInvoke
  def create(@ParamName("period") period: Duration,
             // TODO: @DefaultValue(1) instead of nullable
             @ParamName("count") @Nullable nullableCount: Integer,
             @ParamName("value") value: LazyParameter[jul.Map[String, AnyRef]]): Source[_] = {
    new FlinkSource[jul.Map[String, AnyRef]] with ReturningType {

      override def typeInformation: TypeInformation[jul.Map[String, AnyRef]] = implicitly[TypeInformation[jul.Map[String, AnyRef]]]

      override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[jul.Map[String, AnyRef]] = {
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

        timestampAssigner match {
          case periodic: AssignerWithPeriodicWatermarks[jul.Map[String, AnyRef]@unchecked] =>
            stream.assignTimestampsAndWatermarks(periodic)
          case punctuated: AssignerWithPunctuatedWatermarks[jul.Map[String, AnyRef]@unchecked] =>
            stream.assignTimestampsAndWatermarks(punctuated)
        }
      }

      override def timestampAssignerForTest: Option[TimestampAssigner[jul.Map[String, AnyRef]]] = Some(timestampAssigner)

      override val returnType: typing.TypingResult = value.returnType

    }
  }

}

// CheckpointedFunction to handle checkpoints of underlying stream - not sure if it is need
class PeriodicFunction(duration: Duration) extends SourceFunction[Unit] with CheckpointedFunction {

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

  override def snapshotState(context: FunctionSnapshotContext): Unit = {}

  override def initializeState(context: FunctionInitializationContext): Unit = {}

}

class MapAscendingTimestampExtractor(timestampField: String) extends AscendingTimestampExtractor[jul.Map[String, AnyRef]] {
  override def extractAscendingTimestamp(element: jul.Map[String, AnyRef]): Long = {
    element.asScala
      .get(timestampField).map(_.asInstanceOf[Long])
      .getOrElse(System.currentTimeMillis())
  }
}

object MapAscendingTimestampExtractor {
  val DefaultTimestampField = "timestamp"
}