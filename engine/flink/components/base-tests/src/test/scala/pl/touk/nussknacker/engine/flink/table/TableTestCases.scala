package pl.touk.nussknacker.engine.flink.table

object TableTestCases {

  object SimpleTable {
    val tableName = "testTable"
    val connector = "filesystem"

    val sqlStatement: String =
      s"""|CREATE TABLE testTable
          |(
          |    someString  STRING,
          |    someVarChar VARCHAR(150),
          |    someInt     INT,
          |    someIntComputed AS someInt * 2
          |) WITH (
          |      'connector' = '$connector'
          |);""".stripMargin

  }

}
