package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.api.DataTypes
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class TypeConversionsTest extends AnyFunSuite with Matchers {

  // TODO: do a parametrized test
  test("return correct type for integer") {
    TypeConversions.nuTypeToFlinkTableType(Typed[Int]) shouldBe Some(DataTypes.INT())
    TypeConversions.nuTypeToFlinkTableType(Typed.fromInstance(1)) shouldBe Some(DataTypes.INT())
    TypeConversions.nuTypeToFlinkTableType(Typed[String]) shouldBe Some(DataTypes.STRING())
    TypeConversions.nuTypeToFlinkTableType(Typed.fromInstance("str")) shouldBe Some(DataTypes.STRING())
  }

}
