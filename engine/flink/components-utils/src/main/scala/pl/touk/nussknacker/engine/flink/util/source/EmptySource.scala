package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  StandardFlinkSource,
  StandardFlinkSourceFunctionUtils
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

case class EmptySource(returnType: TypingResult) extends StandardFlinkSource[Any] with ReturningType {

  @silent("deprecated")
  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[Any] =
    StandardFlinkSourceFunctionUtils.createSourceStream(
      env = env,
      sourceFunction = new SourceFunction[Any] {
        override def cancel(): Unit                                    = {}
        override def run(ctx: SourceFunction.SourceContext[Any]): Unit = {}
      },
      typeInformation = TypeInformationDetection.instance.forType[Any](returnType)
    )

}
