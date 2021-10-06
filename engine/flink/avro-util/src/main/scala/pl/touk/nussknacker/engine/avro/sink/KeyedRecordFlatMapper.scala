package pl.touk.nussknacker.engine.avro.sink

import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.configuration.Configuration
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue
import KeyedRecordFlatMapper._


private[sink] object KeyedRecordFlatMapper {

  type Key = AnyRef

  type RecordMap = Map[String, AnyRef]

  def apply(flinkCustomNodeContext: FlinkCustomNodeContext, key: LazyParameter[AnyRef], sinkRecord: AvroSinkRecordValue): KeyedRecordFlatMapper =
    new KeyedRecordFlatMapper(
      flinkCustomNodeContext.lazyParameterHelper,
      key,
      sinkRecord)
}


private[sink] class KeyedRecordFlatMapper(val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                          key: LazyParameter[AnyRef],
                                          sinkRecord: AvroSinkRecordValue)
  extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[AnyRef, AnyRef]]] with LazyParameterInterpreterFunction {

  private val outputType = sinkRecord.typingResult

  private var interpreter: Context => KeyedValue[Key, RecordMap] = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    interpreter = createInterpreter()
  }

  private lazy val emptyRecord: LazyParameter[RecordMap] = lazyParameterInterpreter
    .pure[RecordMap](Map.empty, outputType)

  override def flatMap(value: Context, out: Collector[ValueWithContext[KeyedValue[Key, AnyRef]]]): Unit = {
    collectHandlingErrors(value, out) {
      ValueWithContext(interpreter(value), value)
    }
  }

  private def createInterpreter(): Context => KeyedValue[Key, RecordMap] = {
    implicit val lpi: LazyParameterInterpreter = lazyParameterInterpreter
    val record = merge(emptyRecord, sinkRecord)
    val keyedRecord = key.product(record).map(
      fun = tuple => KeyedValue(tuple._1, tuple._2),
      outputTypingResult = outputType
    )
    lazyParameterInterpreter.syncInterpretationFunction(keyedRecord)
  }

  private def merge(agg: LazyParameter[RecordMap], sinkRecord: AvroSinkRecordValue): LazyParameter[RecordMap] =
    sinkRecord.fields.foldLeft(agg) { case (lazyRecord, (fieldName, fieldSinkValue)) =>
      val lazyParam = fieldSinkValue match {
        case single: AvroSinkSingleValue => single.value
        case sinkRec: AvroSinkRecordValue => merge(emptyRecord, sinkRec)
      }
      implicit val lpi: LazyParameterInterpreter = lazyParameterInterpreter
      lazyRecord.product(lazyParam).map (
        fun = { case (rec, value) => rec + (fieldName -> value)},
        outputTypingResult = outputType
      )
    }
}