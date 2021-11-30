package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableProvidedInRuntime, AdditionalVariableWithFixedValue, NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Source, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationPerformed}
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._

class AdditionalVariableSpec extends FunSuite with Matchers {

  test("detect AdditionalVariable with fixed value") {
    val parameters = definition(new CorrectService)
    parameters.head.additionalVariables shouldBe Map("fixed" -> AdditionalVariableWithFixedValue(CanUseAsFixed(), Typed[CanUseAsFixed]))
    parameters.last.additionalVariables shouldBe Map("runtime" -> AdditionalVariableProvidedInRuntime[CannotUseAsFixed])
  }

  test("handles incorrect fixed value") {
    intercept[IllegalArgumentException] {
      definition(new IncorrectService1)
    }.getMessage shouldBe "Failed to create instance of pl.touk.nussknacker.engine.definition.CannotUseAsFixed, " +
      "it has to have no-arg constructor to be injected as AdditionalVariable"
  }

  test("doesn't allow LazyParameter with fixed value") {
    val modelData = LocalModelData(ConfigFactory.empty(), new CreatorWithComponent(new IncorrectService2))
    val result = NodeDataValidator.validate(node.Source("sid", SourceRef("one", evaluatedparam.Parameter("toFail", "''") :: Nil)),
        modelData, ValidationContext.empty, Map.empty)(MetaData("scenario", StreamMetaData()))
    result.asInstanceOf[ValidationPerformed]
      .errors.distinct shouldBe CannotCreateObjectError("AdditionalVariableWithFixedValue should not be used with LazyParameters", "sid") :: Nil

  }

  private def definition(sourceFactory: SourceFactory): List[Parameter] = {
    ProcessDefinitionExtractor
      .extractObjectWithMethods(new CreatorWithComponent(sourceFactory), ProcessObjectDependencies(ConfigFactory.empty(), DefaultNamespacedObjectNaming))
      .sourceFactories.head._2.parameters
  }

  class CorrectService extends SourceFactory {
    @MethodToInvoke
    def invoke(
                @ParamName("fixed") @AdditionalVariables(Array(new AdditionalVariable(name = "fixed", clazz = classOf[CanUseAsFixed])))
                eagerParam: String,
                @ParamName("runtime") @AdditionalVariables(Array(new AdditionalVariable(name = "runtime", clazz = classOf[CannotUseAsFixed])))
                lazyParam: LazyParameter[String]): Source = null
  }


  class IncorrectService1 extends SourceFactory {
    @MethodToInvoke
    def invoke(@ParamName("fixed") @AdditionalVariables(Array(new AdditionalVariable(name = "fixed", clazz = classOf[CannotUseAsFixed])))
               eagerParam: String): Source = null
  }

  class IncorrectService2 extends SourceFactory with SingleInputGenericNodeTransformation[Source] {

    override type State = Nothing

    override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(List(Parameter[String]("toFail")
        .copy(isLazyParameter = true,
          additionalVariables = Map("failing" -> AdditionalVariableWithFixedValue("wontwork")))))
    }

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[Nothing]): Source = null

    override def nodeDependencies: List[NodeDependency] = Nil

  }

  class CreatorWithComponent(component: SourceFactory) extends EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("one" -> WithCategories(component))
  }

}

case class CanUseAsFixed()

class CannotUseAsFixed(constructor: String)
