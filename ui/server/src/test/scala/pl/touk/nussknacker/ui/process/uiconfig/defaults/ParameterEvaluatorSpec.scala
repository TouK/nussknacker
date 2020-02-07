package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.definition.defaults.NodeDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.reflect.ClassTag

class ParameterEvaluatorSpec extends FlatSpec with Matchers {
  private val DEFAULT_PARAMETER_NAME = "parameter"
  private val DEFAULT_NODE_NAME = "undefined"
  private val DEFAULT_PARAMETER_VALUE = "defVal"
  private val DEFINED_NODE_NAME = "defined"

  private def dummyParam[T:ClassTag](nodeName: String,
                         paramName: String) =
    NodeDefinition(nodeName, List(Parameter[T](paramName)))

  private def dummyExpectedParam(paramName: String, value: Any) = {
    evaluatedparam.Parameter(paramName, Expression("spel", value.toString))
  }

  private def testTypeRelatedDefaultValue[T:ClassTag](value: Any,
                                          paramName: String = DEFAULT_PARAMETER_NAME,
                                          nodeName: String = DEFAULT_NODE_NAME): Unit = {
    it should s"set ${implicitly[ClassTag[T]].runtimeClass} for parameter $paramName as $value in node $nodeName" in {
      val paramIn = dummyParam[T](paramName = paramName,
        nodeName = nodeName)
      val paramOut = dummyExpectedParam(paramName = paramName,
        value = value)
      pv.evaluateParameters(paramIn).head shouldBe paramOut
    }
  }

  private val pv = new ParameterEvaluatorExtractor(
    DefaultValueExtractorChain(
      ParamDefaultValueConfig(
        Map(DEFINED_NODE_NAME -> Map(DEFAULT_PARAMETER_NAME -> ParameterConfig(defaultValue = Some(DEFAULT_PARAMETER_VALUE), editor = None)))
      ),
      ModelClassLoader.empty
    )
  )
  behavior of "ParameterEvaluator"
  testTypeRelatedDefaultValue[Int](value = 0)
  testTypeRelatedDefaultValue[Double](value = 0f)
  testTypeRelatedDefaultValue[Double](value = DEFAULT_PARAMETER_VALUE, nodeName = DEFINED_NODE_NAME)
}
