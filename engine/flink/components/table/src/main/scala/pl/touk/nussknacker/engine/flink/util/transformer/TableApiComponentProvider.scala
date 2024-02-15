package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.Expressions.row
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.api.component.{
  BoundedStreamComponent,
  ComponentDefinition,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.api.process.{
  BasicContextInitializer,
  ContextInitializer,
  ContextInitializingFunction,
  ProcessObjectDependencies,
  Source,
  SourceFactory
}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}

class TableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TableApiComponentProvider.Components
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}

object TableApiComponentProvider {

  lazy val Components: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "tableBoundedSource",
        ExperimentalTableSource
      )
    )

}

object ExperimentalTableSource extends SourceFactory with BoundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new FlinkSource {
      override def sourceStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[Context] = {
        val tableEnv = StreamTableEnvironment.create(env);

        val table = tableEnv.fromValues(
          row(1, "ABC"),
          row(2, "DEF")
        )

        val rowStream: DataStream[Row] = tableEnv.toDataStream(table)

        val mappedToSchemaStream = rowStream.map(r => r.getFieldAs[T](0))

        val contextStream =
          mappedToSchemaStream.map(
            new FlinkContextInitializingFunction(
              contextInitializer,
              flinkNodeContext.nodeId,
              flinkNodeContext.convertToEngineRuntimeContext
            ),
            flinkNodeContext.contextTypeInfo
          )

        contextStream.print()

        contextStream
      }
    }
  }

  // TODO local: type has to be calculated dynamically or based on schema
  type T = Int

  // TODO local: this context initialization was copied from kafka source - check this
  private val contextInitializer: ContextInitializer[T] = new BasicContextInitializer[T](Unknown)

  class FlinkContextInitializingFunction[T](
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
