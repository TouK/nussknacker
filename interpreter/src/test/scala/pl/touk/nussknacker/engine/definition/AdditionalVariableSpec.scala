package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableProvidedInRuntime, AdditionalVariableWithFixedValue, Parameter}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class AdditionalVariableSpec extends FunSuite with Matchers {

  test("detect AdditionalVariable with fixed value") {
    val parameters = definition(new CorrectService)
    parameters.head.additionalVariables shouldBe Map("fixed" -> AdditionalVariableWithFixedValue(CanUseAsFixed(), Typed[CanUseAsFixed]))
    parameters.last.additionalVariables shouldBe Map("runtime" -> AdditionalVariableProvidedInRuntime[CannotUseAsFixed])
  }

  test("doesn't allow LazyParameter with fixed value") {
    intercept[IllegalArgumentException] {
      definition(new IncorrectService2)
    }.getMessage shouldBe "Class should not be used with LazyParameters"
  }

  test("handles incorrect fixed value") {
    intercept[IllegalArgumentException] {
      definition(new IncorrectService1)
    }.getMessage shouldBe "Failed to create instance of pl.touk.nussknacker.engine.definition.CannotUseAsFixed"
  }

  private def definition(sourceFactory: SourceFactory): List[Parameter] = {
    ProcessDefinitionExtractor
      .extractObjectWithMethods(new CreatorWithComponent(sourceFactory), ProcessObjectDependencies(ConfigFactory.empty(), DefaultNamespacedObjectNaming))
      .sourceFactories.head._2.parameters
  }

  class CorrectService extends SourceFactory {
    @MethodToInvoke
    def invoke(
                @ParamName("fixed") @AdditionalVariables(Array(new AdditionalVariable(name = "fixed", clazz = classOf[CanUseAsFixed], initializedByRuntime = true)))
                eagerParam: String,
                @ParamName("runtime") @AdditionalVariables(Array(new AdditionalVariable(name = "runtime", clazz = classOf[CannotUseAsFixed])))
                lazyParam: LazyParameter[String]): Source = null
  }


  class IncorrectService1 extends SourceFactory {
    @MethodToInvoke
    def invoke(@ParamName("fixed") @AdditionalVariables(Array(new AdditionalVariable(name = "fixed", clazz = classOf[CannotUseAsFixed], initializedByRuntime = true)))
               eagerParam: String): Source = null
  }

  class IncorrectService2 extends SourceFactory {
    @MethodToInvoke
    def invoke(@ParamName("fixed") @AdditionalVariables(Array(new AdditionalVariable(name = "fixed", clazz = classOf[CanUseAsFixed], initializedByRuntime = true)))
               eagerParam: LazyParameter[String]): Source = null
  }

  class CreatorWithComponent(component: SourceFactory) extends EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("one" -> WithCategories(component))
  }

}

case class CanUseAsFixed()

class CannotUseAsFixed(constructor: String)
