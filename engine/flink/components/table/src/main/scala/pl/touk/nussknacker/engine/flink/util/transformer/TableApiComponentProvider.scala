package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.{DataTypes, Schema, TableDescriptor}
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  NussknackerVersion,
  UnboundedStreamComponent
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
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}

import scala.jdk.CollectionConverters.mapAsJavaMapConverter

class TableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TableApiComponentProvider.Components
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  // TODO local: just for local development
  override def isAutoLoaded: Boolean = true

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

// TODO: Shouldn't be unbounded - this is just for easier local development
object ExperimentalTableSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def invoke(): Source = {
    new CustomSource()
  }

  private class CustomSource extends FlinkSource with ReturningType {

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);

      tableEnv.createTable(
        "kafkaInput",
        TableDescriptor
          .forConnector("kafka")
          .option("properties.bootstrap.servers", "localhost:13032")
          .option("properties.group.id", "testGroup")
          .option("topic", "input1")
          .option("scan.startup.mode", "earliest-offset")
          .format("json")
          .schema(
            Schema
              .newBuilder()
              .column("id", DataTypes.INT())
              .column("name", DataTypes.STRING())
              .build()
          )
          .build()
      )

      val table = tableEnv.from("kafkaInput")

      val rowStream: DataStream[Row] = tableEnv.toDataStream(table)

      // TODO: Types:
      //  - for catalogs / dynamic components: infer returnType dynamically from table schema based on table.getResolvedSchema.getColumns
      //  - for method based components - get schema from config
      val mappedToSchemaStream = rowStream
        .map(r => {
          val eInt    = r.getFieldAs[Int](0)
          val eString = r.getFieldAs[String](1)
          val fields  = Map("id" -> eInt, "name" -> eString)
          new java.util.HashMap[String, Any](fields.asJava): RECORD
        })
        .returns(classOf[RECORD])

      val contextStream = mappedToSchemaStream.map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )

      contextStream
    }

    // This gets displayed in FE suggestions
    override def returnType: typing.TypedObjectTypingResult = {
      Typed.record(Map("id" -> Typed[Integer], "name" -> Typed[String]))
    }

  }

  private type RECORD = java.util.Map[String, Any]

  // TODO local: this context initialization was copied from kafka source - check this
  private val contextInitializer: ContextInitializer[RECORD] = new BasicContextInitializer[RECORD](Typed[RECORD])

  private class FlinkContextInitializingFunction[T](
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
