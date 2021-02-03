package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.flink.api.process.{FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue

import scala.util.control.NonFatal

private[sink] class KeyedRecordFlatMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                          key: LazyParameter[AnyRef],
                                          sinkRecord: AvroSinkRecordValue)
  extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[AnyRef, AnyRef]]] with LazyParameterInterpreterFunction with LazyLogging {

  private implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter =
    lazyParameterInterpreter

  private lazy val emptyRecord: LazyParameter[Map[String, AnyRef]] = lazyParameterInterpreter
    .pure[Map[String, AnyRef]](Map.empty, typing.Typed[Map[String, AnyRef]])

  private lazy val record: LazyParameter[Map[String, AnyRef]] =
    merge(emptyRecord, sinkRecord)

  private def merge(agg: LazyParameter[Map[String, AnyRef]], sinkRecord: AvroSinkRecordValue): LazyParameter[Map[String, AnyRef]] =
    sinkRecord.fields.foldLeft(agg) { case (lazyRecord, (fieldName, fieldSinkValue)) =>
      val lazyParam = fieldSinkValue match {
        case primitive: AvroSinkPrimitiveValue => primitive.value
        case sinkRec: AvroSinkRecordValue => merge(emptyRecord, sinkRec)
      }
      lazyRecord.product(lazyParam).map { case (rec, value) =>
        rec + (fieldName -> value)
      }
    }

  override def flatMap(value: Context, out: Collector[ValueWithContext[KeyedValue[AnyRef, AnyRef]]]): Unit =
    try out.collect(ValueWithContext(interpret(value), value))
    catch { case NonFatal(e) => logger.error(e.getMessage, e) }

  private def interpret(ctx: Context): keyed.KeyedValue[AnyRef, AnyRef] =
    lazyParameterInterpreter.syncInterpretationFunction(
      key.product(record).map(tuple => KeyedValue(tuple._1, tuple._2))
    )(ctx)
}