package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog.{Column, ResolvedSchema}

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

    val tableDefinition: TableDefinition = TableDefinition(
      tableName,
      ResolvedSchema.of(
        Column.physical("someString", DataTypes.STRING()),
        Column.physical("someVarChar", DataTypes.VARCHAR(150)),
        Column.physical("someInt", DataTypes.INT()),
      )
    )

  }

}
