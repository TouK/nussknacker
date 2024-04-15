package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, StandardFlinkSource}

case class EmptySource[T: TypeInformation](returnType: TypingResult) extends StandardFlinkSource[T] with ReturningType {

  @silent("deprecated")
  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = env.addSource(
    new SourceFunction[T] {
      override def cancel(): Unit                                  = {}
      override def run(ctx: SourceFunction.SourceContext[T]): Unit = {}
    },
    implicitly[TypeInformation[T]]
  )

}
