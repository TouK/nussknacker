package pl.touk.nussknacker.engine.flink.table.generic

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{Context, MethodToInvoke}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSource}
import pl.touk.nussknacker.engine.flink.table.TableApiSourceFactoryMixin

// TODO local: should not be unbounded
class GenericTableSourceFactory(dataSource: DataSourceTable)
    extends SourceFactory
    with UnboundedStreamComponent
    with TableApiSourceFactoryMixin {

  @MethodToInvoke
  def invoke(): Source = {
    new BoundedSource()
  }

  private class BoundedSource extends FlinkSource with ReturningType {

    override def sourceStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      ???
    }

    // This gets displayed in FE suggestions
    override def returnType: typing.TypedObjectTypingResult = {
      ???
    }

  }

}
