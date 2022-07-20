package pl.touk.nussknacker.engine.lite.components

import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.generic.{GenericRecord, IndexedRecord}
import pl.touk.nussknacker.engine.spel.SpELImplicits

object ImplicitsSpELWithAvro {

  import collection.JavaConverters._

  implicit class ImplicitsSpELWithAvro(data: Any) extends SpELImplicits {

    def toSpEL: String = toSpEL(data)

    override def toSpEL(data: Any): String = data match {
      case enum: EnumSymbol => toSpEL(`enum`.toString)
      case fixed: Fixed => toSpEL(fixed.toString)
      case record: GenericRecord => toSpEL(convertRecordToMap(record))
      case any => super.toSpEL(any)
    }

    private def convertRecordToMap(record: IndexedRecord): Map[String, AnyRef] = record.getSchema.getFields.asScala.map { field =>
      val value = record.get(field.pos()) match {
        case rec: IndexedRecord => convertRecordToMap(rec)
        case v => v
      }

      field.name() -> value
    }.toMap
  }

}

