package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.streaming.api.datastream.{DataStream, KeyedStream, SingleOutputStreamOperator}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.keyed.{StringKeyOnlyMapper, StringKeyedValueMapper}
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.reflect.runtime.universe.TypeTag

object richflink {

  implicit class FlinkKeyOperations(dataStream: DataStream[Context]) {

    def groupBy(groupBy: LazyParameter[CharSequence])(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[String], String] = {
      val typeInfo = ctx.typeInformationDetection.forValueWithContext[String](ctx.validationContext.left.get, groupBy.map[String]((k: CharSequence) => k.toString).returnType)
      dataStream
        .flatMap(new StringKeyOnlyMapper(ctx.lazyParameterHelper, groupBy), typeInfo)
        .keyBy((k: ValueWithContext[String]) => k.value)
    }

    def groupByWithValue[T <: AnyRef: TypeTag](groupBy: LazyParameter[CharSequence], value: LazyParameter[T])(implicit ctx: FlinkCustomNodeContext): KeyedStream[ValueWithContext[KeyedValue[String, T]], String] = {
      val typeInfo = keyed.typeInfo(ctx, groupBy.map[String]((k: CharSequence) => k.toString), value)
      dataStream
        .flatMap(new StringKeyedValueMapper(ctx.lazyParameterHelper, groupBy, value), typeInfo)
        .keyBy((k: ValueWithContext[KeyedValue[String, T]]) => k.value.key)
    }
  }

  implicit class ExplicitUid[T](dataStream: DataStream[T]) {

    //we set operator name to nodeId in custom transformers, so that some internal Flink metrics (e.g. RocksDB) are
    //reported with operator_name tag equal to nodeId.
    //in most cases uid should be set together with operator name, if this is not the case - use ExplicitUidInOperatorsSupport explicitly
    def setUidWithName(implicit ctx: FlinkCustomNodeContext, explicitUidInStatefulOperators: FlinkCustomNodeContext => Boolean): DataStream[T] =
      ExplicitUidInOperatorsSupport.setUidIfNeed[T](explicitUidInStatefulOperators(ctx), ctx.nodeId)(dataStream) match {
        case operator: SingleOutputStreamOperator[T] => operator.name(ctx.nodeId)
        case other => other
      }
  }

}
