package pl.touk.nussknacker.engine.flink.util.transformer

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{
  CustomizableTimestampWatermarkHandlerSource,
  FlinkCustomNodeContext,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

import java.time.Duration
import java.time.temporal.ChronoUnit
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

class SampleGeneratorSourceFactory(customTimestampAssigner: TimestampWatermarkHandler[AnyRef])
    extends SourceFactory
    with UnboundedStreamComponent {

  @silent("deprecated")
  @MethodToInvoke
  def create(
      @ParamName("period")
      @DualEditor(
        simpleEditor = new SimpleEditor(
          `type` = SimpleEditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
        ),
        defaultMode = DualEditorMode.SIMPLE
      )
      period: Duration,
      // TODO: @DefaultValue(1) instead of nullable
      @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
      @ParamName("value") value: LazyParameter[AnyRef]
  ): Source = {
    new StandardFlinkSource[AnyRef] with ReturningType with CustomizableTimestampWatermarkHandlerSource[AnyRef] {

      override protected def sourceStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[AnyRef] = {
        val count = Option(nullableCount).map(_.toInt).getOrElse(1)
        // Parameter evaluation requires context, so here we create an empty context just to evaluate the `value` param.
        // Later the evaluated value is extracted from this temporary context and proper context is initialized.
        env
          .addSource(new PeriodicFunction(period))
          .flatMap(
            (_: Unit, out: Collector[Context]) => {
              val temporaryContextForEvaluation = Context(flinkNodeContext.metaData.name.value)
              (1 to count).foreach(_ => out.collect(temporaryContextForEvaluation))
            },
            TypeInformationDetection.instance.forClass[Context]
          )
          .flatMap(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap(
            (value: ValueWithContext[AnyRef], out: Collector[AnyRef]) => out.collect(value.value),
            TypeInformationDetection.instance.forType[AnyRef](value.returnType)
          )
      }

      override def timestampAssigner: Option[TimestampWatermarkHandler[AnyRef]] = Some(customTimestampAssigner)

      override val returnType: typing.TypingResult = value.returnType
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
