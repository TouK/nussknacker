package pl.touk.nussknacker.engine.api.component

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class NodeComponentInfoSpec extends AnyFunSuite with Matchers {

  test("should create NodeComponentInfo for base node") {
    val nodeComponentInfo = NodeComponentInfo.forBaseNode("nodeId", ComponentType.Filter)
    nodeComponentInfo shouldBe NodeComponentInfo("nodeId", "filter", ComponentType.Filter)
  }
}
