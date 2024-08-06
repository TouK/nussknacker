package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

object TableTestCases {

  object SimpleTable {
    val tableName = "testTable"
    val connector = "filesystem"

    val sqlStatement: String =
      s"""|CREATE TABLE testTable
          |(
          |    someString  STRING,
          |    someVarChar VARCHAR(150),
          |    someInt     INT
          |) WITH (
          |      'connector' = '$connector'
          |);""".stripMargin

    val schemaTypingResult: TypingResult = Typed.record(
      Map(
        "someString"  -> Typed[String],
        "someVarChar" -> Typed[String],
        "someInt"     -> Typed[Integer],
      ),
      Typed.typedClass[Row]
    )

    val tableDefinition: TableDefinition = TableDefinition(
      tableName,
      schemaTypingResult,
      columns = List(
        ColumnDefinition("someString", Typed[String], DataTypes.STRING()),
        ColumnDefinition("someVarChar", Typed[String], DataTypes.VARCHAR(150)),
        ColumnDefinition("someInt", Typed[Integer], DataTypes.INT()),
      )
    )

  }

}
