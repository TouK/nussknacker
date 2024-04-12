package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{
  ExplicitTypeInformationSource,
  FlinkCustomNodeContext,
  FlinkStandardSourceUtils,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.jdk.CollectionConverters._

case class CollectionSource[T: TypeInformation](
    list: List[T],
    timestampAssigner: Option[TimestampWatermarkHandler[T]],
    returnType: TypingResult,
    boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED,
    flinkRuntimeMode: Option[RuntimeExecutionMode] = None
) extends StandardFlinkSource[T]
    with ExplicitTypeInformationSource[T]
    with ReturningType {

  @silent("deprecated")
  override def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    // TODO: remove setting runtime mode here after setting it on deployment level
    flinkRuntimeMode.foreach(env.setRuntimeMode)
    boundedness match {
      case Boundedness.BOUNDED =>
        env.fromCollection(list.asJava)
      case Boundedness.CONTINUOUS_UNBOUNDED =>
        FlinkStandardSourceUtils.createSourceStream(
          env = env,
          sourceFunction = new FromElementsFunction[T](list.filterNot(_ == null).asJava),
          typeInformation = typeInformation
        )
    }
  }

  override def typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  override def contextInitializer: ContextInitializer[T] = new BasicContextInitializer[T](Unknown)
}
