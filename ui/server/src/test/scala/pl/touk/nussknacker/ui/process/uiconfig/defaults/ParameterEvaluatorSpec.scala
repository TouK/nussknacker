package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, Parameter}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.ui.api.NodeDefinition

class ParameterEvaluatorSpec extends FlatSpec with Matchers {
  private val DEFAULT_PARAMETER_NAME = "parameter"
  private val DEFAULT_NODE_NAME = "undefined"
  private val DEFAULT_PARAMETER_VALUE = "defVal"
  private val DEFINED_NODE_NAME = "defined"

  private def dummyParam(nodeName: String,
                         paramName: String,
                         classRef: String) =
    NodeDefinition(nodeName, List(Parameter(paramName, ClazzRef(classRef))))

  private def dummyExpectedParam(paramName: String, value: Any) = {
    evaluatedparam.Parameter(paramName, Expression("spel", value.toString))
  }

  private def testTypeRelatedDefaultValue(classRef: String,
                                          value: Any,
                                          paramName: String = DEFAULT_PARAMETER_NAME,
                                          nodeName: String = DEFAULT_NODE_NAME) = {
    it should s"set $classRef for parameter $paramName as $value in node $nodeName" in {
      val paramIn = dummyParam(paramName = paramName,
        classRef = classRef,
        nodeName = nodeName)
      val paramOut = dummyExpectedParam(paramName = paramName,
        value = value)
      pv.evaluateParameters(paramIn).head shouldBe paramOut
    }
  }

  private val pv = new ParameterEvaluatorExtractor(
    new TypeAfterConfig(
      new ParamDefaultValueConfig(
        Map(DEFINED_NODE_NAME -> Map(DEFAULT_PARAMETER_NAME -> DEFAULT_PARAMETER_VALUE))
      )
    )
  )
  behavior of "ParameterEvaluator"
  testTypeRelatedDefaultValue(classRef = "int", value = 0)
  testTypeRelatedDefaultValue(classRef = "double", value = 0f)
  testTypeRelatedDefaultValue(classRef = "double", value = DEFAULT_PARAMETER_VALUE, nodeName = DEFINED_NODE_NAME)
}
