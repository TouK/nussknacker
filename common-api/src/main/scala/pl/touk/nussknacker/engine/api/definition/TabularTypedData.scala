package pl.touk.nussknacker.engine.api.definition

import io.circe
import io.circe.Decoder.Result
import io.circe.syntax.EncoderOps
import io.circe._
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.TabularTypedData.Cell.RawValue
import pl.touk.nussknacker.engine.api.definition.TabularTypedData.{Column, Row}

import scala.util.Try

final case class TabularTypedData private (columns: Vector[Column]) {
  val rows: Vector[Row]                            = columns.transpose(_.cells).map(Row.apply)
  val columnDefinitions: Vector[Column.Definition] = columns.map(_.definition)
}

object TabularTypedData {

  final case class Column(definition: Column.Definition, cells: Vector[Cell])

  object Column {
    final case class Definition(name: String, aType: Class[_])
  }

  final case class Cell(definition: Column.Definition, rawValue: RawValue) {

    def value: Any = rawValue match {
      case RawValue(null) => null
      case RawValue(v)    => fromStringToSupportedClassInstance(v, definition.aType).get
    }

  }

  object Cell {
    final case class RawValue(value: String)
  }

  final case class Row(cells: Vector[Cell])

  type HumanReadableErrorMessage = String

  lazy val empty: TabularTypedData = TabularTypedData(Vector.empty)

  def create(
      columns: Vector[Column.Definition],
      rows: Vector[Vector[RawValue]]
  ): Either[HumanReadableErrorMessage, TabularTypedData] = {
    for {
      _    <- validateColumnNamesUniqueness(columns)
      _    <- validateCellsCount(columns, rows)
      data <- Right(createTabularTypedData(columns, rows))
      _    <- validateCellValuesType(data)
    } yield data
  }

  private def createTabularTypedData(columns: Vector[Column.Definition], rows: Vector[Vector[RawValue]]) = {
    TabularTypedData {
      columns.zipWithIndex
        .map { case (column, idx) =>
          val valuesOfColumn = rows.map { row => row(idx) }
          Column(column, valuesOfColumn.map(Cell(column, _)))
        }
    }
  }

  private def validateColumnNamesUniqueness(
      columns: Vector[Column.Definition]
  ): Either[HumanReadableErrorMessage, Unit] = {
    val originColumnNames = columns.map(_.name)
    val duplicates        = originColumnNames.diff(originColumnNames.distinct)
    if (duplicates.isEmpty) Right(())
    else Left(s"Column names should be unique. Duplicates: ${duplicates.distinct.mkString(",")}")
  }

  private def validateCellsCount(columns: Vector[Column.Definition], rows: Vector[Vector[Any]]) = {
    rows.foldLeft(Right(()): Either[HumanReadableErrorMessage, Unit]) {
      case (r @ Right(()), row) if row.size == columns.size => r
      case (Right(()), _)   => Left("All rows should have the same number of cells as there are columns")
      case (l @ Left(_), _) => l
    }
  }

  private def validateCellValuesType(data: TabularTypedData): Either[HumanReadableErrorMessage, Unit] = {
    data.rows.flatMap(_.cells).foldLeft(Right(()): Either[HumanReadableErrorMessage, Unit]) {
      case (r @ Right(()), cell) if cell.rawValue == RawValue(null) => r
      case (r @ Right(()), cell) if doesCellValueLookOK(cell)       => r
      case (Right(()), cell) =>
        Left(
          s"Column has a '${cell.definition.aType.getCanonicalName}' type but the value '${cell.rawValue.value}' cannot be converted to it."
        )
      case (l @ Left(_), _) => l
    }
  }

  private def doesCellValueLookOK(cell: Cell) = {
    fromStringToSupportedClassInstance(cell.rawValue.value, cell.definition.aType).isSuccess
  }

  private def fromStringToSupportedClassInstance(value: String, toType: Class[_]): Try[Any] = Try {
    toType match {
      case t if classOf[java.lang.String] == t        => value
      case t if classOf[java.lang.Boolean] == t       => stringToJavaBoolean(value)
      case t if classOf[java.lang.Integer] == t       => java.lang.Integer.valueOf(value)
      case t if classOf[java.lang.Float] == t         => java.lang.Float.valueOf(value)
      case t if classOf[java.lang.Double] == t        => java.lang.Double.valueOf(value)
      case t if classOf[java.math.BigInteger] == t    => new java.math.BigInteger(value)
      case t if classOf[java.math.BigDecimal] == t    => new java.math.BigDecimal(value)
      case t if classOf[java.time.LocalDate] == t     => java.time.LocalDate.parse(value)
      case t if classOf[java.time.LocalDateTime] == t => java.time.LocalDateTime.parse(value)
      case t => throw new IllegalArgumentException(s"Type ${t.getCanonicalName} is not supported")
    }
  }

  private def stringToJavaBoolean(value: String) = {
    value.toLowerCase match {
      case "true"  => java.lang.Boolean.TRUE
      case "false" => java.lang.Boolean.FALSE
      case _       => throw new IllegalArgumentException(s"Cannot convert $value to java.lang.Boolean")
    }
  }

  implicit class Stringify(data: TabularTypedData) {

    def stringify: String = Coders.TabularTypedDataEncoder(data).noSpaces
  }

  def fromString(value: String): Either[circe.Error, TabularTypedData] = {
    for {
      json <- io.circe.parser.parse(value)
      data <- Coders.TabularTypedDataDecoder.decodeJson(json)
    } yield data
  }

}

/* example:
{
  "columns": [
    {
      "name": "some name",
      "type": "java.lang.Double"
    },
    {
      "name": "B",
      "type": "java.lang.String"
    },
    {
      "name": "C",
      "type": "java.lang.String"
    }
  ],
  "rows": [
    [
      null,
      null,
      "test"
    ],
    [
      "1",
      "foo",
      "bar"
    ],
    [
      null,
      null,
      "xxx"
    ]
  ]
}
 */
private object Coders {

  object TabularTypedDataEncoder extends Encoder[TabularTypedData] {
    override def apply(data: TabularTypedData): Json = dataEncoder(data)

    private implicit lazy val dataEncoder: Encoder[TabularTypedData] =
      Encoder.forProduct2("columns", "rows")(data => (data.columns, data.rows))

    private implicit lazy val columnEncoder: Encoder[Column] =
      Encoder.forProduct2("name", "type")(column => (column.definition.name, column.definition.aType.getCanonicalName))

    private implicit lazy val rowEncoder: Encoder[Row] = Encoder.instance { row =>
      row.cells.map(_.rawValue).asJson
    }

    private implicit lazy val rawValueEncoder: Encoder[RawValue] = Encoder.encodeJson.contramap {
      case RawValue(null)    => Json.Null
      case RawValue(nonNull) => Json.fromString(nonNull)
    }

  }

  object TabularTypedDataDecoder extends Decoder[TabularTypedData] {

    override def apply(c: HCursor): Result[TabularTypedData] = {
      for {
        columns <- c.downField("columns").as[Vector[Column.Definition]]
        rows    <- c.downField("rows").as[Vector[Vector[RawValue]]]
        data <- TabularTypedData
          .create(columns, rows)
          .left
          .map(message => DecodingFailure(message, List.empty))
      } yield data
    }

    private implicit val classDecoder: Decoder[Class[_]] =
      Decoder.decodeString.emapTry[Class[_]] { str => Try(Class.forName(str)) }

    private implicit val columnDecoder: Decoder[Column.Definition] =
      Decoder.forProduct2("name", "type")(Column.Definition.apply)

    private implicit val rawValueDecoder: Decoder[RawValue] =
      Decoder[Option[String]].map {
        case Some(value) => RawValue(value)
        case None        => RawValue(null)
      }

  }

}
