package pl.touk.nussknacker.ui.definition

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.definition.defaults.{DefaultValueDeterminerChain, ParamDefaultValueConfig, UINodeDefinition}

import scala.reflect.runtime.universe._

class EvaluatedParameterPreparerSpec extends FlatSpec with Matchers {

  private val DEFAULT_PARAMETER_NAME = "parameter"
  private val DEFAULT_NODE_NAME = "undefined"
  private val DEFAULT_PARAMETER_VALUE = "defVal"
  private val DEFINED_NODE_NAME = "defined"
  private val pv = new EvaluatedParameterPreparer(
    DefaultValueDeterminerChain(
      ParamDefaultValueConfig(
        Map(DEFINED_NODE_NAME -> Map(DEFAULT_PARAMETER_NAME -> ParameterConfig(defaultValue = Some(DEFAULT_PARAMETER_VALUE), editor = None, None, None)))
      )
    )
  )

  private def testTypeRelatedDefaultValue[T: TypeTag](value: Any,
                                                      paramName: String = DEFAULT_PARAMETER_NAME,
                                                      nodeName: String = DEFAULT_NODE_NAME): Unit = {
    it should s"set ${implicitly[TypeTag[T]]} for parameter $paramName as $value in node $nodeName" in {
      val paramIn = dummyParam[T](paramName = paramName,
        nodeName = nodeName)
      val paramOut = dummyExpectedParam(paramName = paramName,
        value = value)
      pv.prepareEvaluatedParameter(paramIn).head shouldBe paramOut
    }
  }

  private def dummyParam[T: TypeTag](nodeName: String, paramName: String) =
    UINodeDefinition(nodeName, List(UIProcessObjectsFactory.createUIParameter(Parameter[T](paramName))))

  private def dummyExpectedParam(paramName: String, value: Any) = {
    evaluatedparam.Parameter(paramName, Expression("spel", value.toString))
  }

  behavior of "EvaluatedParameterPreparer"
  testTypeRelatedDefaultValue[Int](value = 0)
  testTypeRelatedDefaultValue[Double](value = 0f)
  testTypeRelatedDefaultValue[Double](value = DEFAULT_PARAMETER_VALUE, nodeName = DEFINED_NODE_NAME)
}
