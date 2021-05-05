package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, StringKeyOnlyMapper, StringKeyedValueMapper, StringKeyedValueWithWindowLengthMapper}

import java.time.Duration
import scala.reflect.runtime.universe.TypeTag

object richflink {

  implicit class FlinkKeyOperations(dataStream: DataStream[Context]) {

    def keyBy(keyBy: LazyParameter[CharSequence])(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[String], String] =
      dataStream
        .map(new StringKeyOnlyMapper(ctx.lazyParameterHelper, keyBy))
        .keyBy((k: ValueWithContext[String]) => k.value)

    def keyByWithValueX[T <: AnyRef: TypeTag: TypeInformation](keyBy: LazyParameter[CharSequence],
                                                               windowLength: LazyParameter[Duration],
                                                               value: LazyParameterInterpreter => LazyParameter[T])
                                                              (implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[KeyedValue[String, (Duration, T)]], String] = {
      dataStream
        .map(new StringKeyedValueWithWindowLengthMapper(ctx.lazyParameterHelper, keyBy, windowLength, value))
        .keyBy((k: ValueWithContext[keyed.KeyedValue[String, (Duration, T)]]) => k.value.key)
    }

    def keyByWithValue[T <: AnyRef: TypeTag: TypeInformation](keyBy: LazyParameter[CharSequence], value: LazyParameterInterpreter => LazyParameter[T])(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[KeyedValue[String, T]], String] =
      dataStream
        .map(new StringKeyedValueMapper(ctx.lazyParameterHelper, keyBy, value))
        .keyBy((k: ValueWithContext[keyed.KeyedValue[String, T]]) => k.value.key)

    def keyByWithValue[T <: AnyRef: TypeTag: TypeInformation](keyBy: LazyParameter[CharSequence], value: LazyParameter[T])(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[KeyedValue[String, T]], String] =
      keyByWithValue(keyBy, _ => value)
  }

  implicit class ExplicitUid[T](dataStream: DataStream[T]) {
    def setUid(implicit ctx: FlinkCustomNodeContext, explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean): DataStream[T] =
      ExplicitUidInOperatorsSupport.setUidIfNeed(explicitUidInStatefulOperators(ctx), ctx.nodeId)(dataStream)
  }


}
