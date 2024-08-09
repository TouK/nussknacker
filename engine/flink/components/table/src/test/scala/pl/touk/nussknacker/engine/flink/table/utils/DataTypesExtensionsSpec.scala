package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.table.api.DataTypes
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions.LogicalTypeExtension

class DataTypesExtensionsSpec extends AnyFunSuiteLike with Matchers {

  test("to typing result conversion for raw type") {
    val anyRefDataType = DataTypes
      .RAW[AnyRef](classOf[AnyRef], TypeInformation.of(classOf[AnyRef]).createSerializer(new ExecutionConfig()))
    anyRefDataType.getLogicalType.toTypingResult shouldEqual Unknown
  }

}
