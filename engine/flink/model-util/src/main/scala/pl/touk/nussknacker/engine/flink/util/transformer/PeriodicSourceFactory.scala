package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{BasicContextInitializingFunction, FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}

import java.time.Duration
import java.{util => jul}
import javax.annotation.Nullable
import javax.validation.constraints.Min
import scala.collection.JavaConverters._

// TODO: add testing capabilities
object PeriodicSourceFactory extends PeriodicSourceFactory(
  new StandardTimestampWatermarkHandler[AnyRef](WatermarkStrategy.forMonotonousTimestamps()
    .withTimestampAssigner(new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField))))

class PeriodicSourceFactory(timestampAssigner: TimestampWatermarkHandler[AnyRef]) extends SourceFactory[AnyRef]  {

  @MethodToInvoke
  def create(@ParamName("period") period: Duration,
             // TODO: @DefaultValue(1) instead of nullable
             @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
             @ParamName("value") value: LazyParameter[AnyRef]): Source[_] = {
    new FlinkSource[AnyRef] with ReturningType {

      override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {

        val count = Option(nullableCount).map(_.toInt).getOrElse(1)
        val processId = flinkNodeContext.metaData.id
        val stream = env
          .addSource(new PeriodicFunction(period))
          .map(_ => Context(processId))
          .flatMap(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap { v =>
            1.to(count).map(_ => v.value)
          }

        val rawSourceWithTimestamp = timestampAssigner.assignTimestampAndWatermarks(stream)

        val typeInformationFromNodeContext = flinkNodeContext.typeInformationDetection.forContext(flinkNodeContext.validationContext.left.get)
        rawSourceWithTimestamp
          .map(new BasicContextInitializingFunction[AnyRef](flinkNodeContext.metaData.id, flinkNodeContext.nodeId))(typeInformationFromNodeContext)
      }

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

class MapAscendingTimestampExtractor(timestampField: String) extends SerializableTimestampAssigner[AnyRef] {

  override def extractTimestamp(element: scala.AnyRef, recordTimestamp: Long): Long = {
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