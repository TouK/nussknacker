package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.{DataStream, KeyedStream}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.{GenericKeyedValueMapper, KeyOptions, StringKeyOnlyMapper}
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.reflect.runtime.universe.TypeTag

object richflink {

  implicit class FlinkKeyOperations(dataStream: DataStream[Context]) {

    def groupBy(
        groupBy: LazyParameter[CharSequence],
        groupByParameterName: ParameterName
    )(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[String], String] =
      dataStream
        .flatMap(
          new StringKeyOnlyMapper(
            ctx.lazyParameterHelper,
            groupBy,
            groupByParameterName,
            KeyOptions(allowNullableKeys = false)
          ),
          ctx.valueWithContextInfo.forClass[String]
        )
        .keyBy((k: ValueWithContext[String]) => k.value)

    def groupByWithValue[T <: AnyRef: TypeTag, K <: AnyRef: TypeTag](
        groupBy: LazyParameter[K],
        groupByParameterName: ParameterName,
        value: LazyParameter[T],
        preserveContext: Boolean,
    )(
        implicit ctx: FlinkCustomNodeContext
    ): KeyedStream[ValueWithContext[KeyedValue[K, T]], K] = {
      val typeInfo = keyed.typeInfo(ctx, groupBy, value)
      val keyedValueMapper = new GenericKeyedValueMapper(
        ctx.lazyParameterHelper,
        groupBy,
        groupByParameterName,
        KeyOptions(allowNullableKeys = false),
        value
      )

      if (preserveContext) {
        dataStream
          .flatMap(keyedValueMapper, typeInfo)
          .keyBy((k: ValueWithContext[KeyedValue[K, T]]) => k.value.key)
      } else {
        dataStream
          .flatMap(keyedValueMapper, typeInfo)
          .map(new ClearUserVariables)
          .keyBy((k: ValueWithContext[KeyedValue[K, T]]) => k.value.key)
      }
    }

  }

  private class ClearUserVariables[K, V]
      extends MapFunction[ValueWithContext[KeyedValue[K, V]], ValueWithContext[KeyedValue[K, V]]] {
    override def map(value: ValueWithContext[KeyedValue[K, V]]): ValueWithContext[KeyedValue[K, V]] =
      value.copy(context = value.context.clearUserVariables)
  }

}
