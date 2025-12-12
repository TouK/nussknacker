package pl.touk.nussknacker.engine.flink.util.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.Boundedness
import org.apache.flink.connector.datagen.functions.FromElementsGeneratorFunction
import org.apache.flink.connector.datagen.source.DataGeneratorSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

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
    val rawSource = env
      .fromSource(
        flinkSource(list, env.getConfig, flinkNodeContext),
        watermarkStrategy.getOrElse(WatermarkStrategy.noWatermarks()),
        flinkNodeContext.nodeId.id
      )
      .uid(flinkNodeContext.nodeId.id)

    rawSource
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  protected def flinkSource(
      list: List[T],
      executionConfig: ExecutionConfig,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataGeneratorSource[T] = {
    val typeInformation   = TypeInformationDetection.instance.forType[T](returnType)
    val generatorFunction = new FromElementsGeneratorFunction[T](typeInformation, executionConfig, list.asJava)

    new DataGeneratorSource[T](generatorFunction, list.size, typeInformation) {
      override def getBoundedness: Boundedness = boundedness
    }
  }

}
