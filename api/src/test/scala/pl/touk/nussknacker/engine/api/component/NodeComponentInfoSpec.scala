package pl.touk.nussknacker.engine.api.component

import org.scalatest.{FunSuite, Matchers}

class NodeComponentInfoSpec extends FunSuite with Matchers {

  test("should create NodeComponentInfo for base node") {
    val nodeComponentInfo = NodeComponentInfo.forBaseNode("nodeId", ComponentType.Filter)
    nodeComponentInfo shouldBe NodeComponentInfo("nodeId", "filter", ComponentType.Filter)
  }
}
