package pl.touk.nussknacker.engine.flink.api.process

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{
  BasicContextInitializer,
  ContextInitializer,
  ContextInitializingFunction,
  Source
}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler

object FlinkStandardSourceUtils extends ExplicitUidInOperatorsSupport {

  def defaultContextInitializer[Raw] = new BasicContextInitializer[Raw](Unknown)

  @silent("deprecated")
  def createSource[Raw](
      env: StreamExecutionEnvironment,
      sourceFunction: SourceFunction[Raw],
      typeInformation: TypeInformation[Raw]
  ): DataStreamSource[Raw] = {
    env.addSource[Raw](sourceFunction, typeInformation)
  }

  def prepareSource[Raw](
      source: DataStreamSource[Raw],
      flinkNodeContext: FlinkCustomNodeContext,
      timestampAssigner: Option[TimestampWatermarkHandler[Raw]] = None,
      customContextInitializer: Option[ContextInitializer[Raw]] = None,
  ): DataStream[Context] = {

    // 1. set UID and override source name
    val rawSourceWithUid = setUidToNodeIdIfNeed[Raw](
      flinkNodeContext,
      source
        .name(flinkNodeContext.nodeId)
    )

    // 2. assign timestamp and watermark policy
    val rawSourceWithUidAndTimestamp = timestampAssigner
      .map(_.assignTimestampAndWatermarks(rawSourceWithUid))
      .getOrElse(rawSourceWithUid)

    // 3. initialize Context and spool Context to the stream
    rawSourceWithUidAndTimestamp
      .map(
        new FlinkContextInitializingFunction(
          customContextInitializer.getOrElse(defaultContextInitializer),
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

}

class FlinkContextInitializingFunction[Raw](
    contextInitializer: ContextInitializer[Raw],
    nodeId: String,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends RichMapFunction[Raw, Context] {

  private var initializingStrategy: ContextInitializingFunction[Raw] = _

  override def open(parameters: Configuration): Unit = {
    val contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
    initializingStrategy = contextInitializer.initContext(contextIdGenerator)
  }

  override def map(input: Raw): Context = {
    initializingStrategy(input)
  }

}
