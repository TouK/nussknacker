package pl.touk.nussknacker.engine.lite.components.utils

import org.apache.avro.generic.{GenericRecord, IndexedRecord}
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import pl.touk.nussknacker.test.LiteralSpEL

object LiteralSpELWithAvroImplicits {

  import scala.jdk.CollectionConverters._

  implicit class LiteralSpELWithAvroImplicits(data: Any) extends LiteralSpEL {

    def toSpELLiteral: String = toSpELLiteral(data)

    override protected def toSpELLiteral(data: Any): String = data match {
      case enum: EnumSymbol      => toSpELLiteral(`enum`.toString)
      case fixed: Fixed          => toSpELLiteral(fixed.toString)
      case record: GenericRecord => toSpELLiteral(convertRecordToMap(record))
      case any                   => super.toSpELLiteral(any)
    }

    private def convertRecordToMap(record: IndexedRecord): Map[String, AnyRef] =
      record.getSchema.getFields.asScala.map { field =>
        val value = record.get(field.pos()) match {
          case rec: IndexedRecord => convertRecordToMap(rec)
          case v                  => v
        }

        field.name() -> value
      }.toMap

  }

}
