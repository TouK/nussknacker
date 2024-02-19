package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer, ContextInitializingFunction}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed

trait TableApiComponent {

  // TODO local: type has to be calculated dynamically or based on schema
  protected type RECORD = java.util.Map[String, Any]

  // TODO local: this context initialization was copied from kafka source - check this
  protected val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

  protected class FlinkContextInitializingFunction[T](
      contextInitializer: ContextInitializer[T],
      nodeId: String,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext
  ) extends RichMapFunction[T, Context] {

    private var initializingStrategy: ContextInitializingFunction[T] = _

    override def open(parameters: Configuration): Unit = {
      val contextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
      initializingStrategy = contextInitializer.initContext(contextIdGenerator)
    }

    override def map(input: T): Context = {
      initializingStrategy(input)
    }

  }

}
