package pl.touk.esp.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}

class ParamDefaultValueConfigTest extends FlatSpec with Matchers {
  private val definedValue = "defined"
  private val undefined = "undefined"
  private val other = "other"
  val values = new ParamDefaultValueConfig(
    Map((definedValue, Map(
      (definedValue, definedValue),
      (other, other)
    )))
  )

  def verify(node: String, parameter: String, value: Option[String]): Unit =
    it should s"find $value for node $node in param $parameter" in {
      values.getNodeValue(node, parameter) shouldBe value
    }

  behavior of "ParamDefaultValueConfig"
  verify(node = undefined, parameter = definedValue, None)
  verify(node = undefined, parameter = undefined, None)
  verify(node = definedValue, parameter = definedValue, Some(definedValue))
  verify(node = definedValue, parameter = undefined, None)

  verify(node = definedValue, parameter = other, Some(other))
}
