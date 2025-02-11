package pl.touk.nussknacker.engine.flink.util.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  StandardFlinkSource,
  StandardFlinkSourceFunctionUtils
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.jdk.CollectionConverters._

case class CollectionSource[T](
    list: List[T],
    override val timestampAssigner: Option[TimestampWatermarkHandler[T]],
    override val returnType: TypingResult,
    boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED
) extends StandardFlinkSource[T]
    with ReturningType {

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    createSourceStream(list, env, flinkNodeContext)
  }

  @silent("deprecated")
  protected def createSourceStream[T](
      list: List[T],
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    val typeInformation = TypeInformationDetection.instance.forType[T](returnType)
    boundedness match {
      case Boundedness.BOUNDED =>
        env.fromCollection(list.asJava, typeInformation)
      case Boundedness.CONTINUOUS_UNBOUNDED =>
        StandardFlinkSourceFunctionUtils.createSourceStream(
          env = env,
          sourceFunction = new FromElementsFunction[T](list.filterNot(_ == null).asJava),
          typeInformation = typeInformation
        )
    }
  }

}
