package pl.touk.nussknacker.engine.definition

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{LazyInterpreter, PossibleValues}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.StringValues

class ParameterTypeMapperTest extends FlatSpec with Matchers {

  private def run(id: String) {}
  private def runAnnotated(@PossibleValues(Array("a", "b", "c", "d")) id: String) {}
  private def runAnnotatedLazy(@PossibleValues(Array("a", "b", "c")) id: LazyInterpreter[String]) {}

  private val param = getFirstParam("run", classOf[String])

  private val paramAnnotated = getFirstParam("runAnnotated", classOf[String])
  
  private val paramLazyAnnotated = getFirstParam("runAnnotatedLazy", classOf[LazyInterpreter[String]])


  it should "detect @PossibleValues annotation" in {

    ParameterTypeMapper.prepareRestrictions(classOf[String], param) shouldBe None

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramAnnotated) shouldBe Some(StringValues(List("a", "b", "c", "d")))

    ParameterTypeMapper.prepareRestrictions(classOf[String], paramLazyAnnotated) shouldBe Some(StringValues(List("a", "b", "c")))


  }

  private def getFirstParam(name: String, params: Class[_] *) = {
    this.getClass.getDeclaredMethod(name, params: _*).getParameters.apply(0)
  }

}



