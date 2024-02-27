package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.types.DataType
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object HardcodedSchema {

  import scala.jdk.CollectionConverters._

  val stringColumnName = "someString"
  val intColumnName    = "someInt"

  val schema: Schema = Schema
    .newBuilder()
    .column("someString", DataTypes.STRING())
    .column("someInt", DataTypes.INT())
    .build()

  val rowDataType: DataType = DataTypes.ROW(
    DataTypes.FIELD(stringColumnName, DataTypes.STRING()),
    DataTypes.FIELD(intColumnName, DataTypes.INT()),
  )

  val typingResult: TypingResult = Typed.record(Map("someInt" -> Typed[Integer], "someString" -> Typed[String]))

  object MapRowConversion {

    def toMap(row: Row): java.util.HashMap[String, Any] = {
      val intVal    = row.getFieldAs[Int](intColumnName)
      val stringVal = row.getFieldAs[String](stringColumnName)
      val fields    = Map(intColumnName -> intVal, stringColumnName -> stringVal)
      new java.util.HashMap[String, Any](fields.asJava)
    }

    def fromMap(map: java.util.Map[String, Any]): Row = {
      val stringVal: String = map.get(stringColumnName).asInstanceOf[String]
      val intVal: Int       = map.get(intColumnName).asInstanceOf[Int]

      val row = Row.withNames()
      row.setField(stringColumnName, stringVal)
      row.setField(intColumnName, intVal)
      row
    }

  }

}
