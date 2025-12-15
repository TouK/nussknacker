package pl.touk.nussknacker.engine.flink.util.source

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.connector.source.{Boundedness, Source}
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
    with ReturningType
    with LazyLogging {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    env
      .fromSource(
        flinkSource(list, env.getConfig, flinkNodeContext),
        watermarkStrategy.getOrElse(WatermarkStrategy.noWatermarks()),
        flinkNodeContext.nodeId.id,
      )
      .uid(flinkNodeContext.nodeId.id)
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
  ): Source[T, _, _] = {
    val typeInformation  = TypeInformationDetection.instance.forType[T](returnType)
    val listWithoutNulls = handleNullsInData(list)
    // DataGeneratorSource will throw NoSuchElementException from FromElementsGeneratorFunction.tryDeserialize
    // when created with an empty element list
    if (listWithoutNulls.isEmpty) {
      new EmptySource[T](boundedness, typeInformation)
    } else {
      val generatorFunction =
        new FromElementsGeneratorFunction[T](typeInformation, executionConfig, listWithoutNulls.asJava)

      val copyOfBoundedness = boundedness
      new DataGeneratorSource[T](generatorFunction, listWithoutNulls.size, typeInformation) {
        override def getBoundedness: Boundedness = copyOfBoundedness
      }
    }
  }

  // FromElementsGeneratorFunction doesn't accept nulls in the passed collection - see constructor
  // so we filter them out
  // TODO: we probably should resolve this problem otherwise either by delivering our own GeneratorFunction
  //       or checking this collection on early stage, but before we do that we should check every places
  //       where this source is used
  private def handleNullsInData(list: List[T]) = {
    if (list.contains(null)) {
      logger.warn("Collection passed to CollectionSource contains null. They will be filtered out")
    }
    list.filterNot(_ == null)
  }

}
