package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.Types
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NullableTypeInfoTest extends AnyFunSuite with Matchers {

  test("creates a comparator that handles null") {
    val comparator = new NullableTypeInfo[java.lang.Long](Types.LONG).createComparator(true, new ExecutionConfig)

    comparator.compare(null, 1L) should be < 0
    comparator.compare(1L, null) should be > 0
  }

}
