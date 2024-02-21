package pl.touk.nussknacker.engine.flink.table.generic

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.TableApiSourceFactoryMixin

import scala.reflect.ClassTag

// TODO local: should not be unbounded
class GenericTableSourceFactory(dataSource: DataSourceTable)
    extends SourceFactory
    with UnboundedStreamComponent
    with TableApiSourceFactoryMixin {

  @MethodToInvoke
  def invoke(): Source = {
    new GenericTableSource()
  }

  private class GenericTableSource extends FlinkSource with ReturningType {

    import scala.jdk.CollectionConverters._

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val tableEnv = StreamTableEnvironment.create(env);
      tableEnv.executeSql(dataSource.sqlCreateTableStatement);

      val table                      = tableEnv.from(dataSource.name)
      val rowStream: DataStream[Row] = tableEnv.toDataStream(table)

      val mappedToSchemaStream = rowStream
        .map(r => {
          // TODO local: how to make this work? Try if hardcoding the values for some case will help
          val fields = dataSource.schema.columns.map { case Column(name, dt) =>
            name -> {
              val classTag: ClassTag[_] = ClassTag(dt.getConversionClass)
              val castedObject = r.getField(name) match {
                case value if classTag.runtimeClass.isInstance(value) => dt.getConversionClass.cast(r)
                case _ => throw new ClassCastException("Cannot cast object to conversion class")
              }
              castedObject
            }
          }.toMap
          val map: RECORD = new java.util.HashMap[String, Any](fields.asJava)
          map
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
      val typedColumns = dataSource.schema.columns.map { case Column(name, dataType) =>
        name -> Typed.typedClass(dataType.getConversionClass)
      }.toMap
      Typed.record(
        typedColumns
      )
    }

  }

}
