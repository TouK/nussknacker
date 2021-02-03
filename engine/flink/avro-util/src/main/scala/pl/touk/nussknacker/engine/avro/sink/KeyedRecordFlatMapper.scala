package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.{RichFlatMapFunction, RuntimeContext}
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionHandler, WithFlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue


private[sink] class KeyedRecordFlatMapper(
                                          protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                          protected val exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler ,
                                          nodeId: String,
                                          key: LazyParameter[AnyRef],
                                          fields: List[(String, LazyParameter[AnyRef])])
  extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[AnyRef, AnyRef]]]
    // mixin order is crucial here for setting lazyParameterInterpreter variable
    with WithFlinkEspExceptionHandler with LazyParameterInterpreterFunction
{

  private implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter =
    lazyParameterInterpreter

  private def emptyRecord: LazyParameter[Map[String, AnyRef]] = lazyParameterInterpreter
    .pure[Map[String, AnyRef]](Map.empty, typing.Typed[Map[String, AnyRef]])

  private lazy val record: LazyParameter[Map[String, AnyRef]] =
    fields.foldLeft(emptyRecord) { case (lazyRecord, (fieldName, lazyParam)) =>
      lazyRecord.product(lazyParam).map { case (rec, param) =>
        rec + (fieldName -> param)
      }
    }

  override def flatMap(value: Context, out: Collector[ValueWithContext[KeyedValue[AnyRef, AnyRef]]]): Unit =
    exceptionHandler.handling(Some(nodeId), value) {
      out.collect(ValueWithContext(interpret(value), value))
    }

  private def interpret(ctx: Context): keyed.KeyedValue[AnyRef, AnyRef] =
    lazyParameterInterpreter.syncInterpretationFunction(
      key.product(record).map(tuple => KeyedValue(tuple._1, tuple._2))
    )(ctx)
}