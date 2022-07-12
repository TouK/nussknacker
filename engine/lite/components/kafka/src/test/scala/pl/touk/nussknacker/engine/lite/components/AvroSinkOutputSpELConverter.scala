package pl.touk.nussknacker.engine.lite.components

import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.generic.{GenericRecord, IndexedRecord}
import pl.touk.nussknacker.test.SinkOutputSpELConverter

import java.time.LocalTime
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec

object AvroSinkOutputSpELConverter extends SinkOutputSpELConverter {

  import collection.JavaConverters._

  private implicit class IndexedRecordConverter(record: IndexedRecord) {
    def toMap: Map[String, AnyRef] = record.getSchema.getFields.asScala.map { field =>
      val value = record.get(field.pos()) match {
        case rec: IndexedRecord => rec.toMap
        case v => v
      }

      field.name() -> value
    }.toMap
  }

  @tailrec
  override def convertToMap(field: String, data: Any): String = data match {
    case enum: EnumSymbol => convertToMap(field, `enum`.toString)
    case fixed: Fixed => convertToMap(field, fixed.toString)
    case record: GenericRecord => convertToMap(field, record.toMap)
    case any => super.convertToMap(field, any)
  }

  override def convert(data: Any, isField: Boolean = false): String = data match {
    case enum: EnumSymbol => convert(`enum`.toString, isField)
    case fixed: Fixed => convert(fixed.toString, isField)
    case record: GenericRecord => convert(record.toMap)
    case any => super.convert(any, isField)
  }
}
