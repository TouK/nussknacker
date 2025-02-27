package pl.touk.nussknacker.engine.graph.expression

import cats.data.NonEmptyList
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Cell.RawValue
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.Column
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.CreationError.{
  CellsCountInRowDifferentThanColumnsCount,
  ColumnNameUniquenessViolation,
  InvalidCellValues
}
import pl.touk.nussknacker.engine.graph.expression.TabularTypedData.CreationError.InvalidCellValues.CellCoordinates

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
      result should be(Left(ColumnNameUniquenessViolation(NonEmptyList.of("A"))))
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
      result should be(
        Left(
          InvalidCellValues(
            NonEmptyList.of(
              CellCoordinates(Column.Definition("C", classOf[java.lang.Object]), rowIndex = 0),
              CellCoordinates(Column.Definition("C", classOf[java.lang.Object]), rowIndex = 1),
              CellCoordinates(Column.Definition("C", classOf[java.lang.Object]), rowIndex = 2)
            ),
            List(
              Column.Definition("A", classOf[java.math.BigDecimal]),
              Column.Definition("B", classOf[java.lang.String]),
              Column.Definition("C", classOf[java.lang.Object])
            )
          )
        )
      )
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
      result should be(Left(CellsCountInRowDifferentThanColumnsCount))
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
      result should be(Left(CellsCountInRowDifferentThanColumnsCount))
    }
    "cannot convert some cell values to declared column's type when" - {
      "the type is java.time.LocalDate" in {
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
        result should be(
          Left(
            InvalidCellValues(
              NonEmptyList.of(
                CellCoordinates(Column.Definition("A", classOf[java.time.LocalDate]), rowIndex = 1)
              ),
              List(
                Column.Definition("A", classOf[java.time.LocalDate]),
                Column.Definition("B", classOf[java.lang.String]),
                Column.Definition("C", classOf[java.lang.Integer]),
              )
            )
          )
        )
      }
      "the type is java.lang.Boolean" in {
        val result = TabularTypedData.create(
          columns = Vector(
            Column.Definition("A", classOf[java.lang.Boolean]),
            Column.Definition("B", classOf[java.lang.String]),
            Column.Definition("C", classOf[java.lang.Integer]),
          ),
          rows = Vector(
            Vector(RawValue("TRUE"), RawValue(null), RawValue("1")),
            Vector(RawValue("False"), RawValue("foo"), RawValue("2")),
            Vector(RawValue("fałsz"), RawValue(null), RawValue("3")),
          )
        )
        result should be(
          Left(
            InvalidCellValues(
              NonEmptyList.of(
                CellCoordinates(Column.Definition("A", classOf[java.lang.Boolean]), rowIndex = 2)
              ),
              List(
                Column.Definition("A", classOf[java.lang.Boolean]),
                Column.Definition("B", classOf[java.lang.String]),
                Column.Definition("C", classOf[java.lang.Integer]),
              )
            )
          )
        )
      }
    }
  }

}
