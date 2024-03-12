package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.types.DataType
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object HardcodedSchema {

  val stringColumnName = "someString"
  val intColumnName    = "someInt"

  val schema: Schema = Schema
    .newBuilder()
    .column(stringColumnName, DataTypes.STRING())
    .column(intColumnName, DataTypes.INT())
    .build()

  val rowDataType: DataType = DataTypes.ROW(
    DataTypes.FIELD(stringColumnName, DataTypes.STRING()),
    DataTypes.FIELD(intColumnName, DataTypes.INT()),
  )

  val typingResult: TypingResult = Typed.record(Map(intColumnName -> Typed[Integer], stringColumnName -> Typed[String]))

  object MapRowConversion {

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
