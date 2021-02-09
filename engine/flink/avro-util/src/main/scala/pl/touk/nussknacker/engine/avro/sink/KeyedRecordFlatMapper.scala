package pl.touk.nussknacker.engine.avro.sink

import org.apache.flink.api.common.functions.{RichFlatMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue

private[sink] object KeyedRecordFlatMapper {
  def apply(flinkCustomNodeContext: FlinkCustomNodeContext, key: LazyParameter[AnyRef], sinkRecord: AvroSinkRecordValue): KeyedRecordFlatMapper =
    new KeyedRecordFlatMapper(
      flinkCustomNodeContext.nodeId,
      flinkCustomNodeContext.lazyParameterHelper,
      flinkCustomNodeContext.exceptionHandlerPreparer,
      key,
      sinkRecord)
}


private[sink] class KeyedRecordFlatMapper(nodeId: String,
                                          lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                          exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler,
                                          key: LazyParameter[AnyRef],
                                          sinkRecord: AvroSinkRecordValue)
  extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[AnyRef, AnyRef]]] {

  private var exceptionHandler: FlinkEspExceptionHandler = _

  private implicit var lazyParameterInterpreter: LazyParameterInterpreter = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    exceptionHandler = exceptionHandlerPreparer(getRuntimeContext)
    lazyParameterInterpreter = lazyParameterHelper.createInterpreter(getRuntimeContext)
  }

  override def close(): Unit = {
    super.close()
    Option(exceptionHandler).foreach(_.close())
    Option(lazyParameterInterpreter).foreach(_.close())
  }

  private lazy val emptyRecord: LazyParameter[Map[String, AnyRef]] = lazyParameterInterpreter
    .pure[Map[String, AnyRef]](Map.empty, typing.Typed[Map[String, AnyRef]])

  private lazy val record: LazyParameter[Map[String, AnyRef]] =
    merge(emptyRecord, sinkRecord)

  // TODO: May affect performance: tests needed
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
    exceptionHandler.handling(Some(nodeId), value) {
      out.collect(ValueWithContext(interpret(value), value))
    }.orNull

  private def interpret(ctx: Context): keyed.KeyedValue[AnyRef, AnyRef] =
    lazyParameterInterpreter.syncInterpretationFunction(
      key.product(record).map(tuple => KeyedValue(tuple._1, tuple._2))
    )(ctx)
}