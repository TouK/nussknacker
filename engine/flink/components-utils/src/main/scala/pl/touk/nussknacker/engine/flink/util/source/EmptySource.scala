package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkStandardSourceUtils,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

case class EmptySource[T: TypeInformation](returnType: TypingResult) extends StandardFlinkSource[T] with ReturningType {

  @silent("deprecated")
  override def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    FlinkStandardSourceUtils.createSourceStream(
      env = env,
      sourceFunction = new SourceFunction[T] {
        override def cancel(): Unit                                  = {}
        override def run(ctx: SourceFunction.SourceContext[T]): Unit = {}
      },
      typeInformation = implicitly[TypeInformation[T]]
    )
  }

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = None

  override def contextInitializer: ContextInitializer[T] = new BasicContextInitializer[T](Unknown)
}
