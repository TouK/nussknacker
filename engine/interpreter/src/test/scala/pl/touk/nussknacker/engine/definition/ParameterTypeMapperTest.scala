package pl.touk.nussknacker.engine.definition

import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{LazyParameter, PossibleValues}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedExpressionValues}
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.types.JavaSampleEnum

class ParameterTypeMapperTest extends FunSuite with Matchers {

  private def run(id: String) {}
  private def runAnnotated(@PossibleValues(Array("a", "b", "c", "d")) id: String) {}
  private def runAnnotatedLazy(@PossibleValues(Array("a", "b", "c")) id: LazyParameter[String]) {}
  private def runEnum(id: JavaSampleEnum) {}

  private val param = getFirstParam("run", classOf[String])

  private val paramAnnotated = getFirstParam("runAnnotated", classOf[String])
  
  private val paramLazyAnnotated = getFirstParam("runAnnotatedLazy", classOf[LazyParameter[String]])


  test("detect @PossibleValues annotation") {

    ParameterTypeMapper.prepareRestrictions(classOf[String], param, ParameterConfig.empty) shouldBe None

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramAnnotated, ParameterConfig.empty) shouldBe Some(FixedExpressionValues(List("a", "b", "c", "d").map(v => FixedExpressionValue(s"'$v'", v))))

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramLazyAnnotated, ParameterConfig.empty) shouldBe Some(FixedExpressionValues(List("a", "b", "c").map(v => FixedExpressionValue(s"'$v'", v))))

  }

  test("detect enums") {

    ParameterTypeMapper.prepareRestrictions(classOf[JavaSampleEnum], getFirstParam("runEnum", classOf[JavaSampleEnum]), ParameterConfig.empty) shouldBe Some(
      FixedExpressionValues(List(
        FixedExpressionValue("T(pl.touk.nussknacker.engine.types.JavaSampleEnum).FIRST_VALUE", "first_value"),
        FixedExpressionValue("T(pl.touk.nussknacker.engine.types.JavaSampleEnum).SECOND_VALUE", "second_value")
      ))
    )

  }

  test("ignore annotations if explicit restriction given") {
    val config = ParameterConfig(None, Some(FixedExpressionValues(List(FixedExpressionValue("lab1", "'v1'")))))

    ParameterTypeMapper.prepareRestrictions(classOf[String], param, config) shouldBe config.restriction

    
    ParameterTypeMapper.prepareRestrictions(classOf[String], paramAnnotated, config) shouldBe config.restriction
  }

  private def getFirstParam(name: String, params: Class[_] *) = {
    Some(this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0))
  }

}



