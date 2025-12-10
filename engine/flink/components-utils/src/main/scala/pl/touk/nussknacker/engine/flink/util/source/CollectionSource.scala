package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.FromElementsFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

case class CollectionSource[T](
    list: List[T],
    watermarkStrategy: Option[WatermarkStrategy[T]],
    override val returnType: TypingResult,
    boundedness: Boundedness = Boundedness.CONTINUOUS_UNBOUNDED
) extends FlinkSource
    with CustomizableContextInitializerSource[T]
    with ReturningType {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    // 1. set UID and override source name
    val rawSourceWithUid = sourceStream(env, flinkNodeContext).setUidAndNameToNodeId(flinkNodeContext.nodeId)

    // 2. assign timestamp and watermark policy
    val rawSourceWithUidAndTimestamp = watermarkStrategy
      .map(rawSourceWithUid.assignTimestampsAndWatermarks)
      .getOrElse(rawSourceWithUid)

    // 3. initialize Context and spool Context to the stream
    rawSourceWithUidAndTimestamp
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  private def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    createSourceStream(list, env, flinkNodeContext)
  }

  @nowarn("cat=deprecation")
  protected def createSourceStream(
      list: List[T],
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    val typeInformation = TypeInformationDetection.instance.forType[T](returnType)
    boundedness match {
      case Boundedness.BOUNDED =>
        env.fromData(recordsListDependingOnBoundedness(list).asJava, typeInformation)
      case Boundedness.CONTINUOUS_UNBOUNDED =>
        env.addSource(new FromElementsFunction[T](recordsListDependingOnBoundedness(list).asJava), typeInformation)
    }
  }

  private def recordsListDependingOnBoundedness(records: List[T]): List[T] = boundedness match {
    case Boundedness.BOUNDED =>
      records
    case Boundedness.CONTINUOUS_UNBOUNDED =>
      // For some reasons, probable some internal implementation of FromElementsFunction, we skip nulls.
      // It might be tricky because this null might by important
      // TODO: document better why we do this + consider other approaches such as: using some other implementation than
      //       FromElementsFunction or checking if input list has nulls and fast failing instead
      records.filterNot(_ == null)
  }

}
