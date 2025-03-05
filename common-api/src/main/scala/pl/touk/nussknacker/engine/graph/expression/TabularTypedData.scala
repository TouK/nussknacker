package pl.touk.nussknacker.engine.graph.expression

import cats.data.NonEmptyList
import io.circe._
import io.circe.Decoder.Result
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.{Column, Row}
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Cell.RawValue
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.CreationError.{
  CellsCountInRowDifferentThanColumnsCount,
  ColumnNameUniquenessViolation,
  InvalidCellValues
}
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.CreationError.InvalidCellValues.CellCoordinates
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Error.{JsonParsingError, ValidationError}

import scala.util.Try

final case class TabularTypedData private (columns: Vector[Column]) {
  val rows: Vector[Row] = columns.transpose(_.cells).zipWithIndex.map { case (rowCells, idx) => Row(idx, rowCells) }
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

  final case class Row(index: Int, cells: Vector[Cell])

  lazy val empty: TabularTypedData = TabularTypedData(Vector.empty)

  sealed trait CreationError

  object CreationError {
    final case class ColumnNameUniquenessViolation(columnNames: NonEmptyList[String]) extends CreationError
    case object CellsCountInRowDifferentThanColumnsCount                              extends CreationError
    final case class InvalidCellValues(cells: NonEmptyList[CellCoordinates], columns: List[Column.Definition])
        extends CreationError

    object InvalidCellValues {
      final case class CellCoordinates(columnName: Column.Definition, rowIndex: Int)
    }

  }

  def create(
      columns: Vector[Column.Definition],
      rows: Vector[Vector[RawValue]]
  ): Either[CreationError, TabularTypedData] = {
    for {
      _ <- validateColumnNamesUniqueness(columns)
      _ <- validateCellsCount(columns, rows)
      data = createTabularTypedData(columns, rows)
      _ <- validateCellValuesType(data)
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

  private def validateColumnNamesUniqueness(columns: Vector[Column.Definition]): Either[CreationError, Unit] = {
    val originColumnNames = columns.map(_.name)
    val duplicates        = originColumnNames.diff(originColumnNames.distinct)
    NonEmptyList.fromFoldable(duplicates) match {
      case None      => Right(())
      case Some(nel) => Left(ColumnNameUniquenessViolation(nel))
    }
  }

  private def validateCellsCount(columns: Vector[Column.Definition], rows: Vector[Vector[Any]]) = {
    rows.foldLeft(Right(()): Either[CreationError, Unit]) {
      case (r @ Right(()), row) if row.size == columns.size => r
      case (Right(()), _)                                   => Left(CellsCountInRowDifferentThanColumnsCount)
      case (l @ Left(_), _)                                 => l
    }
  }

  private def validateCellValuesType(data: TabularTypedData): Either[CreationError, Unit] = {
    val cellErrors = data.rows.flatMap(validateCellsInRow)
    NonEmptyList.fromFoldable(cellErrors) match {
      case None    => Right(())
      case Some(e) => Left(InvalidCellValues(e, data.columnDefinitions.toList))
    }
  }

  private def validateCellsInRow(row: Row) = {
    row.cells.flatMap {
      case cell if cell.rawValue == RawValue(null) => None
      case cell if doesCellValueLookOK(cell)       => None
      case cell                                    => Some(CellCoordinates(cell.definition, row.index))
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

    def stringify: String = Coders
      .TabularTypedDataEncoder(
        (data.columnDefinitions, data.rows.map(_.cells.map(_.rawValue)))
      )
      .noSpaces

  }

  def fromString(value: String): Either[Error, TabularTypedData] = {
    for {
      json <- io.circe.parser.parse(value).left.map(failure => JsonParsingError(failure.message))
      data <- Coders.TabularTypedDataDecoder.decodeJson(json).left.map(failure => JsonParsingError(failure.message))
      (columns, rows) = data
      tabularTypedData <- TabularTypedData.create(columns, rows).left.map(ValidationError.apply)
    } yield tabularTypedData
  }

  sealed trait Error

  object Error {
    final case class JsonParsingError(message: String)     extends Error
    final case class ValidationError(error: CreationError) extends Error
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

  type NotValidatedTabularTypedData = (Vector[Column.Definition], Vector[Vector[RawValue]])

  object TabularTypedDataEncoder extends Encoder[NotValidatedTabularTypedData] {
    override def apply(data: NotValidatedTabularTypedData): Json = dataEncoder(data)

    private implicit lazy val dataEncoder: Encoder[NotValidatedTabularTypedData] =
      Encoder.forProduct2("columns", "rows")(data => (data._1, data._2))

    private implicit lazy val columnEncoder: Encoder[Column.Definition] =
      Encoder.forProduct2("name", "type")(definition => (definition.name, definition.aType.getCanonicalName))

    private implicit lazy val rawValueEncoder: Encoder[RawValue] = Encoder.encodeJson.contramap {
      case RawValue(null)    => Json.Null
      case RawValue(nonNull) => Json.fromString(nonNull)
    }

  }

  object TabularTypedDataDecoder extends Decoder[NotValidatedTabularTypedData] {

    override def apply(c: HCursor): Result[NotValidatedTabularTypedData] = {
      for {
        columns <- c.downField("columns").as[Vector[Column.Definition]]
        rows    <- c.downField("rows").as[Vector[Vector[RawValue]]]
      } yield (columns, rows)
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
