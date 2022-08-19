package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSource, FlinkContextInitializingFunction, FlinkCustomNodeContext}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

import scala.collection.JavaConverters._

case class CollectionSource[T: TypeInformation](list: List[T],
                                                timestampAssigner: Option[TimestampWatermarkHandler[T]],
                                                returnType: TypingResult
                                               ) extends BasicFlinkSource[T] with ReturningType {
  override def flinkSourceFunction = new FromElementsFunction[T](typeInformation.createSerializer(StreamExecutionEnvironment.getExecutionEnvironment.getConfig), list.filterNot(_ == null).asJava)

  override val typeInformation: TypeInformation[T] = implicitly[TypeInformation[T]]

  override def sourceStream(env: StreamExecutionEnvironment, flinkNodeContext: FlinkCustomNodeContext): DataStream[Context] = {
    env.fromCollection(list.filterNot(_ == null))

    val rawSourceWithUid = setUidToNodeIdIfNeed(flinkNodeContext, env
      .fromCollection(list.filterNot(_ == null))
      .name(flinkNodeContext.nodeId))

    //3. assign timestamp and watermark policy
    val rawSourceWithUidAndTimestamp = timestampAssigner
      .map(_.assignTimestampAndWatermarks(rawSourceWithUid))
      .getOrElse(rawSourceWithUid)

    //4. initialize Context and spool Context to the stream
    val typeInformationFromNodeContext = flinkNodeContext.typeInformationDetection.forContext(flinkNodeContext.validationContext.left.get)
    val nodeId = flinkNodeContext.nodeId
    rawSourceWithUidAndTimestamp
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer, nodeId,
          flinkNodeContext.convertToEngineRuntimeContext)
      )(typeInformationFromNodeContext)
  }
}
