package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.{Column, Row}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode

import java.time.temporal.ChronoUnit
import scala.util.Try

@ConfiguredJsonCodec sealed trait ParameterEditor

case object RawParameterEditor extends ParameterEditor

@ConfiguredJsonCodec sealed trait SimpleParameterEditor extends ParameterEditor

case object BoolParameterEditor extends SimpleParameterEditor

case object StringParameterEditor extends SimpleParameterEditor

case object DateParameterEditor extends SimpleParameterEditor

case object TimeParameterEditor extends SimpleParameterEditor

case object DateTimeParameterEditor extends SimpleParameterEditor

case object TextareaParameterEditor extends SimpleParameterEditor

case object JsonParameterEditor extends SimpleParameterEditor

case object SqlParameterEditor extends SimpleParameterEditor

case object SpelTemplateParameterEditor extends SimpleParameterEditor

case object TabularTypedDataEditor extends SimpleParameterEditor {

  // todo: check + smart constructor
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

@JsonCodec case class DurationParameterEditor(timeRangeComponents: List[ChronoUnit]) extends SimpleParameterEditor

object DurationParameterEditor {

  implicit val chronoUnitEncoder: Encoder[ChronoUnit] = {
    new Encoder[ChronoUnit] {
      override def apply(chronoUnit: ChronoUnit): Json = Encoder.encodeString(chronoUnit.name())
    }
  }

  implicit val chronoUnitDecoder: Decoder[ChronoUnit] = {
    Decoder.decodeString.emapTry(name => Try(ChronoUnit.valueOf(name)))
  }

}

@JsonCodec case class PeriodParameterEditor(timeRangeComponents: List[ChronoUnit]) extends SimpleParameterEditor

object PeriodParameterEditor {

  implicit val chronoUnitEncoder: Encoder[ChronoUnit] = {
    new Encoder[ChronoUnit] {
      override def apply(chronoUnit: ChronoUnit): Json = Encoder.encodeString(chronoUnit.name())
    }
  }

  implicit val chronoUnitDecoder: Decoder[ChronoUnit] = {
    Decoder.decodeString.emapTry(name => Try(ChronoUnit.valueOf(name)))
  }

}

/* To use this editor you have to:
  - add https://github.com/jmrozanec/cron-utils to model classpath
  - add CronDefinitionBuilder, CronParser and CronType to additional classes in ExpressionConfig
 */
case object CronParameterEditor extends SimpleParameterEditor

@JsonCodec case class FixedValuesParameterEditor(possibleValues: List[FixedExpressionValue])
    extends SimpleParameterEditor

@JsonCodec case class DualParameterEditor(simpleEditor: SimpleParameterEditor, defaultMode: DualEditorMode)
    extends ParameterEditor

object DualParameterEditor {

  implicit val dualEditorModeEncoder: Encoder[DualEditorMode] = {
    new Encoder[DualEditorMode] {
      override def apply(editorMode: DualEditorMode): Json = Encoder.encodeString(editorMode.name())
    }
  }

  implicit val decodeDualEditorMode: Decoder[DualEditorMode] = {
    Decoder.decodeString.emapTry(name => Try(DualEditorMode.fromName(name)))
  }

}
