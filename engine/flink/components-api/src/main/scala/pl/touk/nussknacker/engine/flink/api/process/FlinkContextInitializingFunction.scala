package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction, RuntimeContext}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ContextInitializingFunction}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext

class FlinkContextInitializingFunction[Raw](
    contextInitializer: ContextInitializer[Raw],
    nodeId: String,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
) extends RichMapFunction[Raw, Context] {

  private var initializingStrategy: ContextInitializingFunction[Raw] = _

  override def open(openContext: OpenContext): Unit = {
    val contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
    initializingStrategy = contextInitializer.initContext(contextIdGenerator)
  }

  override def map(input: Raw): Context = {
    initializingStrategy(input)
  }

}
