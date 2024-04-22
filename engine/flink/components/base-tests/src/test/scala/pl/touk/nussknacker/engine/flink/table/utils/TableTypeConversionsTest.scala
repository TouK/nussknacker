package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.DataTypes
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class TableTypeConversionsTest extends AnyFunSuite with Matchers {

  // TODO: do a parametrized test
  test("return correct type for primitive types") {
    TableTypeConversions.nuTypeToFlinkTableType(Typed[Int]) shouldBe Some(DataTypes.INT())
    TableTypeConversions.nuTypeToFlinkTableType(Typed.fromInstance(1)) shouldBe Some(DataTypes.INT())
    TableTypeConversions.nuTypeToFlinkTableType(Typed[String]) shouldBe Some(DataTypes.STRING())
    TableTypeConversions.nuTypeToFlinkTableType(Typed.fromInstance("str")) shouldBe Some(DataTypes.STRING())
  }

}
