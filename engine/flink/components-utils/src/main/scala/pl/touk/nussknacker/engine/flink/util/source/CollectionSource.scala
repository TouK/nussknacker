package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.ContextInitializer
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{
  CustomContextInitializerSource,
  FlinkCustomNodeContext,
  FlinkIntermediateRawSourceUtils,
  FlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.jdk.CollectionConverters._

// TODO: remove returnType?
case class CollectionSource[T: TypeInformation](
    list: List[T],
    timestampAssigner: Option[TimestampWatermarkHandler[T]],
    returnType: TypingResult,
    boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
    customContextInitializer: Option[ContextInitializer[T]] = None,
    flinkRuntimeMode: Option[RuntimeExecutionMode] = None
) extends FlinkSource
    with ReturningType
    with CustomContextInitializerSource[T] {

  @silent("deprecated")
  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val source = boundedness match {
      case Boundedness.BOUNDED =>
        env.fromCollection(list.asJava)
      case Boundedness.CONTINUOUS_UNBOUNDED =>
        FlinkIntermediateRawSourceUtils.createSource(
          env = env,
          sourceFunction = new FromElementsFunction[T](list.filterNot(_ == null).asJava),
          typeInformation = implicitly[TypeInformation[T]]
        )
    }
    flinkRuntimeMode.foreach(env.setRuntimeMode)
    FlinkIntermediateRawSourceUtils.prepareSource(source, flinkNodeContext, timestampAssigner, customContextInitializer)
  }

  override def contextInitializer: ContextInitializer[T] =
    customContextInitializer.getOrElse(FlinkIntermediateRawSourceUtils.defaultContextInitializer)
}
