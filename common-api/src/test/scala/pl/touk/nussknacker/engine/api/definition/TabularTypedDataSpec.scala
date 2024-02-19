package pl.touk.nussknacker.engine.api.definition

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.TabularTypedData.Cell.RawValue
import pl.touk.nussknacker.engine.api.definition.TabularTypedData.Column

class TabularTypedDataSpec extends AnyFreeSpec with Matchers {

  "TabularTypedData should not able to be created when" - {
    "column names are not unique" in {
      val result = TabularTypedData.create(
        columns = Vector(
          Column.Definition("A", classOf[java.math.BigDecimal]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("A", classOf[java.lang.Integer]),
        ),
        rows = Vector(
          Vector(RawValue(null), RawValue(null), RawValue("1")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("2")),
          Vector(RawValue(null), RawValue(null), RawValue("3")),
        )
      )
      result should be(Left("Column names should be unique. Duplicates: A"))
    }
    "unsupported column's type is used" in {
      val result = TabularTypedData.create(
        columns = Vector(
          Column.Definition("A", classOf[java.math.BigDecimal]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("C", classOf[java.lang.Object]),
        ),
        rows = Vector(
          Vector(RawValue(null), RawValue(null), RawValue("1")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("2")),
          Vector(RawValue(null), RawValue(null), RawValue("3")),
        )
      )
      result should be(Left("Column has a java.lang.Object type but the value 1 cannot be converted to it."))
    }
    "there are more cells in some rows than the columns number" in {
      val result = TabularTypedData.create(
        columns = Vector(
          Column.Definition("A", classOf[java.math.BigDecimal]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("C", classOf[java.lang.Integer]),
        ),
        rows = Vector(
          Vector(RawValue(null), RawValue(null), RawValue("1")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("2"), RawValue("1")),
          Vector(RawValue(null), RawValue(null), RawValue("3")),
        )
      )
      result should be(Left("All rows should have the same number of cells as there are columns"))
    }
    "there are less cells in some rows than the columns number" in {
      val result = TabularTypedData.create(
        columns = Vector(
          Column.Definition("A", classOf[java.math.BigDecimal]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("C", classOf[java.lang.Integer]),
        ),
        rows = Vector(
          Vector(RawValue(null), RawValue(null), RawValue("1")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("2")),
          Vector(RawValue(null), RawValue(null)),
        )
      )
      result should be(Left("All rows should have the same number of cells as there are columns"))
    }
    "cannot convert some cell values to declared column's type" in {
      val result = TabularTypedData.create(
        columns = Vector(
          Column.Definition("A", classOf[java.time.LocalDate]),
          Column.Definition("B", classOf[java.lang.String]),
          Column.Definition("C", classOf[java.lang.Integer]),
        ),
        rows = Vector(
          Vector(RawValue("2024-01-01"), RawValue(null), RawValue("1")),
          Vector(RawValue("1.0"), RawValue("foo"), RawValue("2")),
          Vector(RawValue(null), RawValue(null), RawValue("3")),
        )
      )
      result should be(Left("Column has a java.time.LocalDate type but the value 1.0 cannot be converted to it."))
    }
  }

}
