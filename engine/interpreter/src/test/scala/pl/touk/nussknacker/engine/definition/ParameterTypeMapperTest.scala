package pl.touk.nussknacker.engine.definition

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{LazyInterpreter, PossibleValues}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{FixedExpressionValue, FixedExpressionValues}
import pl.touk.nussknacker.engine.types.JavaSampleEnum

class ParameterTypeMapperTest extends FlatSpec with Matchers {

  private def run(id: String) {}
  private def runAnnotated(@PossibleValues(Array("a", "b", "c", "d")) id: String) {}
  private def runAnnotatedLazy(@PossibleValues(Array("a", "b", "c")) id: LazyInterpreter[String]) {}
  private def runEnum(id: JavaSampleEnum) {}

  private val param = getFirstParam("run", classOf[String])

  private val paramAnnotated = getFirstParam("runAnnotated", classOf[String])
  
  private val paramLazyAnnotated = getFirstParam("runAnnotatedLazy", classOf[LazyInterpreter[String]])


  it should "detect @PossibleValues annotation" in {

    ParameterTypeMapper.prepareRestrictions(classOf[String], param) shouldBe None

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramAnnotated) shouldBe Some(FixedExpressionValues(List("a", "b", "c", "d").map(v => FixedExpressionValue(s"'$v'", v))))

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramLazyAnnotated) shouldBe Some(FixedExpressionValues(List("a", "b", "c").map(v => FixedExpressionValue(s"'$v'", v))))

  }

  it should "detect enums" in {

    ParameterTypeMapper.prepareRestrictions(classOf[JavaSampleEnum], getFirstParam("runEnum", classOf[JavaSampleEnum])) shouldBe Some(
      FixedExpressionValues(List(
        FixedExpressionValue("T(pl.touk.nussknacker.engine.types.JavaSampleEnum).FIRST_VALUE", "first_value"),
        FixedExpressionValue("T(pl.touk.nussknacker.engine.types.JavaSampleEnum).SECOND_VALUE", "second_value")
      ))
    )

  }

  private def getFirstParam(name: String, params: Class[_] *) = {
    Some(this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0))
  }

}



