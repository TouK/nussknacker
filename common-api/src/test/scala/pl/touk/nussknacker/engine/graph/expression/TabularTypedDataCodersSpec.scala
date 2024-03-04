package pl.touk.nussknacker.engine.graph.expression

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Cell.RawValue
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Column

class TabularTypedDataCodersSpec extends AnyFreeSpec with Matchers {

  "we should be able to encode and then decode" - {
    "empty data" in {
      roundTripCoding(TabularTypedData.empty) should be(TabularTypedData.empty)
    }
    "some non-trivial data" in {
      val data = unsafeCreateTabularTypedData(
        columns = Vector(
          Column.Definition("some name", classOf[java.math.BigDecimal]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("C", classOf[java.lang.String]),
        ),
        rows = Vector(
          Vector(RawValue(null), RawValue(null), RawValue("test")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("bar")),
          Vector(RawValue(null), RawValue(null), RawValue("xxx")),
        )
      )

      roundTripCoding(data) should be(data)
    }
  }

  private def unsafeCreateTabularTypedData(columns: Vector[Column.Definition], rows: Vector[Vector[RawValue]]) = {
    TabularTypedData
      .create(columns, rows)
      .left
      .map(error => new RuntimeException(error.toString))
      .toTry
      .get
  }

  private def roundTripCoding = {
    (encode _ andThen decode).andThen { case (columns, rows) => TabularTypedData.create(columns, rows).toOption.get }
  }

  private def encode(data: TabularTypedData) = {
    Coders
      .TabularTypedDataEncoder(
        (data.columnDefinitions, data.rows.map(_.cells.map(_.rawValue)))
      )
      .spaces2
  }

  private def decode(value: String) = {
    val json = io.circe.parser.parse(value).toTry.get
    Coders.TabularTypedDataDecoder.decodeJson(json).toTry.get
  }

}
