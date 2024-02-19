package pl.touk.nussknacker.engine.api.definition

import io.circe
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
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

  final case class Cell(definition: Column.Definition, value: Any)
  final case class Row(cells: Vector[Cell])

  def create(columns: Vector[Column.Definition], rows: Vector[Vector[Any]]): Try[TabularTypedData] = Try {
    TabularTypedData {
      columns.zipWithIndex
        .map { case (column, idx) =>
          val valuesOfColumn = rows.map { row =>
            row.lift(idx) match {
              case Some(value) => value
              case None        => throw new IllegalArgumentException("More columns than rows")
            }
          }
          Column(column, valuesOfColumn.map(Cell(column, _)))
        }
    }
  }

  lazy val empty: TabularTypedData = TabularTypedData(Vector.empty)

  implicit class Stringify(data: TabularTypedData) {

    def stringify: String = Coders.encoder(data).noSpaces
  }

  def fromString(value: String): Either[circe.Error, TabularTypedData] = {
    for {
      json <- io.circe.parser.parse(value)
      data <- TabularTypedData.Coders.decoder.decodeJson(json)
    } yield data
  }

  object Coders {

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
          1,
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
    implicit val encoder: Encoder[TabularTypedData] = Encoder.instance { data =>
      Json.obj(
        "columns" -> Json.arr(
          data.columns
            .map { c =>
              Json.obj(
                "name" -> Json.fromString(c.definition.name),
                "type" -> Json.fromString(c.definition.aType.getCanonicalName)
              )
            }: _*
        ),
        "rows" -> Json.arr(
          data.rows
            .map(_.cells.map(_.value))
            .map { rowValues =>
              Json.arr(
                rowValues.map {
                  case null          => Json.Null
                  case v: String     => Json.fromString(v)
                  case v: Int        => Json.fromInt(v)
                  case v: Long       => Json.fromLong(v)
                  case v: Double     => Json.fromDoubleOrNull(v)
                  case v: Float      => Json.fromFloatOrNull(v)
                  case v: BigDecimal => Json.fromBigDecimal(v)
                  case v: BigInt     => Json.fromBigInt(v)
                  case v: Boolean    => Json.fromBoolean(v)
                  case c             => throw new IllegalArgumentException(s"Unexpected type: ${c.getClass.getName}")
                }: _*
              )
            }: _*
        )
      )
    }

    // todo: are we sure keys are unique
    implicit val decoder: Decoder[TabularTypedData] = {
      implicit val classDecoder: Decoder[Class[_]] = Decoder.decodeString.emapTry[Class[_]] { str =>
        Try(Class.forName(str))
      }
      implicit val columnDecoder: Decoder[Column.Definition] =
        Decoder.forProduct2("name", "type")(Column.Definition.apply)
      implicit val valueDecoder: Decoder[Any] = Decoder.decodeJson.emapTry { value =>
        Try { // todo: validate if type is declared one (and try to cast)
          value.asNull.map(_ => null).getOrElse {
            value.asString.getOrElse {
              value.asBoolean.getOrElse {
                value.asNumber.flatMap(_.toBigDecimal).getOrElse {
                  throw new IllegalArgumentException("Value unexpected type")
                }
              }
            }
          }
        }
      }
      Decoder.instance { c =>
        for {
          columns <- c.downField("columns").as[Vector[Column.Definition]]
          rows    <- c.downField("rows").as[Vector[Vector[Any]]]
        } yield TabularTypedData.create(columns, rows).get
      }
    }

  }

}
