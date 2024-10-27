package pl.touk.nussknacker.engine.definition.model

import cats.data.Validated.Valid
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{
  AdditionalVariableProvidedInRuntime,
  FixedExpressionValue,
  FixedValuesValidator,
  MandatoryParameterValidator,
  Parameter,
  RegExpParameterValidator
}
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.{
  MethodBasedComponentDefinitionWithImplementation,
  MethodBasedComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.modelconfig.{
  ComponentsUiConfigParser,
  DefaultModelConfigLoader,
  InputConfigDuringExecution
}
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import java.time.Duration
import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

class ModelDefinitionFromConfigCreatorExtractorSpec extends AnyFunSuite with Matchers with OptionValues {

  private val SomeCategory      = "SomeCategory"
  private val SomeOtherCategory = "SomeOtherCategory"

  test("extract additional variables info from annotation") {
    val methodDef = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformer1")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]
      .implementationInvoker
      .asInstanceOf[MethodBasedComponentImplementationInvoker]
      .methodDef
    val additionalVars = methodDef.definedParameters.head.additionalVariables
    additionalVars("var1") shouldBe AdditionalVariableProvidedInRuntime[OnlyUsedInAdditionalVariable]
  }

  test("extract type info from classes from additional variables") {
    val classDefinition = modelDefinitionWithTypes(None).classDefinitions.get(classOf[OnlyUsedInAdditionalVariable])
    classDefinition.map(_.methods.keySet) shouldBe Some(Set("someField", "toString"))
  }

  test("extract type info from additional classes") {
    val classDefinition = modelDefinitionWithTypes(None).classDefinitions.get(classOf[AdditionalClass])
    classDefinition.map(_.methods.keySet) shouldBe Some(Set("someField", "toString"))
  }

  test("extract definition from WithExplicitMethodToInvoke") {
    val definition =
      modelDefinitionWithTypes(None).modelDefinition.getComponent(ComponentType.Service, "configurable1").value

    definition
      .asInstanceOf[DynamicComponentDefinitionWithImplementation]
      .component
      .asInstanceOf[EagerServiceWithStaticParametersAndReturnType]
      .returnType shouldBe Typed[String]
  }

  test("extract definition with generic params") {
    val definition = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerWithGenericParam")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]

    definition.parameters should have size 1
    definition.parameters.head.typ shouldEqual Typed.fromDetailedType[List[String]]
  }

  test("extract definition using ContextTransformation") {
    modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerReturningContextTransformationWithOutputVariable")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]
      .returnType shouldBe defined
    modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerReturningContextTransformationWithoutOutputVariable")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]
      .returnType shouldBe empty
  }

  test("extract validators based on editor") {
    val definition = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerWithFixedValueParam")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]

    definition.parameters should have size 1
    val parameter = definition.parameters.head
    parameter.validators should contain(MandatoryParameterValidator)
    parameter.validators should contain(
      FixedValuesValidator(List(FixedExpressionValue("'foo'", "foo"), FixedExpressionValue("'bar'", "bar")))
    )
  }

  test("extract default value from annotation") {
    val definition = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerWithDefaultValueForParameter")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]

    definition.parameters should have size 1
    val parameter = definition.parameters.head
    parameter.defaultValue shouldEqual Some(Expression.spel("'foo'"))
  }

  test("default value from annotation should have higher priority than optionality") {
    val definition = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerWithOptionalDefaultValueForParameter")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]

    definition.parameters should have size 1
    val parameter = definition.parameters.head
    parameter.defaultValue shouldEqual Some(Expression.spel("'foo'"))
  }

  test("extract definition with branch params") {
    val definition = modelDefinitionWithTypes(None).modelDefinition
      .getComponent(ComponentType.CustomComponent, "transformerWithBranchParam")
      .value
      .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]

    definition.parameters should have size 2

    val lazyParam = definition.parameters.head
    lazyParam.branchParam shouldBe true
    lazyParam.isLazyParameter shouldBe true
    lazyParam.typ shouldEqual Typed[Integer]

    val eagerParam = definition.parameters.apply(1)
    eagerParam.branchParam shouldBe true
    eagerParam.isLazyParameter shouldBe false
    eagerParam.typ shouldEqual Typed[Integer]
  }

  test("extract basic global variable") {
    val definition = modelDefinitionWithTypes(None).modelDefinition.expressionConfig.globalVariables

    val helperDef = definition("helper")
    helperDef.objectWithType(MetaData("dumb", StreamMetaData())).obj shouldBe SampleHelper
    helperDef.typ shouldBe Typed(SampleHelper.getClass)
  }

  test("extract typed global variable") {
    val definition = modelDefinitionWithTypes(None).modelDefinition.expressionConfig.globalVariables

    val typedGlobalDef = definition("typedGlobal")
    val objectWithType = typedGlobalDef.objectWithType(MetaData("dumb", StreamMetaData()))
    objectWithType.obj shouldBe SampleTypedVariable.Value
    objectWithType.typ shouldBe Typed.fromInstance(SampleTypedVariable.Value)
  }

  test("extracts validators from config") {
    val definition =
      modelDefinitionWithTypes(None).modelDefinition
        .getComponent(ComponentType.CustomComponent, "transformer1")
        .value
        .asInstanceOf[MethodBasedComponentDefinitionWithImplementation]
    val parameter = definition.parameters.find(_.name == ParameterName("param1"))
    parameter.map(_.validators) shouldBe Some(
      List(
        MandatoryParameterValidator,
        RegExpParameterValidator(".*", "has to match...", "really has to match...")
      )
    )
  }

  test("extract components that are only in specified category") {
    val customTransformersIds =
      modelDefinitionWithTypes(Some(SomeCategory)).modelDefinition.components.components.map(_.id)

    customTransformersIds should contain(ComponentId(ComponentType.CustomComponent, "transformer1"))
    customTransformersIds should contain(ComponentId(ComponentType.CustomComponent, "transformedInSomeCategory"))
    customTransformersIds should not contain ComponentId(
      ComponentType.CustomComponent,
      "transformedInSomeOtherCategory"
    )

  }

  private def modelDefinitionWithTypes(category: Option[String]) = {
    val modelConfig = new DefaultModelConfigLoader(_ => true)
      .resolveConfig(InputConfigDuringExecution(ConfigFactory.empty()), getClass.getClassLoader)
    val modelDefinition = ModelDefinitionFromConfigCreatorExtractor.extractModelDefinition(
      TestCreator,
      category,
      ProcessObjectDependencies.withConfig(modelConfig),
      ComponentsUiConfigParser.parse(modelConfig),
      id => DesignerWideComponentId(id.toString),
      Map.empty
    )
    ModelDefinitionWithClasses(modelDefinition)
  }

  object TestCreator extends ProcessConfigCreator {

    override def customStreamTransformers(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "transformer1"                -> WithCategories.anyCategory(Transformer1),
        "transformerWithGenericParam" -> WithCategories.anyCategory(TransformerWithGenericParam),
        "transformerReturningContextTransformationWithOutputVariable" -> WithCategories.anyCategory(
          TransformerReturningContextTransformationWithOutputVariable
        ),
        "transformerReturningContextTransformationWithoutOutputVariable" -> WithCategories.anyCategory(
          TransformerReturningContextTransformationWithoutOutputVariable,
        ),
        "transformerWithBranchParam"     -> WithCategories.anyCategory(TransformerWithBranchParam),
        "transformerWithFixedValueParam" -> WithCategories.anyCategory(TransformerWithFixedValueParam),
        "transformerWithDefaultValueForParameter" -> WithCategories.anyCategory(
          TransformerWithDefaultValueForParameter
        ),
        "transformerWithOptionalDefaultValueForParameter" -> WithCategories.anyCategory(
          TransformerWithOptionalDefaultValueForParameter,
        ),
        "transformedInSomeCategory"      -> WithCategories(Transformer1, SomeCategory),
        "transformedInSomeOtherCategory" -> WithCategories(Transformer1, SomeOtherCategory),
      )

    override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
      Map(
        "configurable1" -> WithCategories.anyCategory(
          EmptyExplicitMethodToInvoke(
            List(Parameter[Int](ParameterName("param1")), Parameter[Duration](ParameterName("durationParam"))),
            Typed[String]
          )
        )
      )

    override def sourceFactories(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] = Map()

    override def sinkFactories(
        modelDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SinkFactory]] = Map()

    override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

    override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig =
      ExpressionConfig(
        globalProcessVariables = Map(
          "helper"      -> WithCategories.anyCategory(SampleHelper),
          "typedGlobal" -> WithCategories.anyCategory(SampleTypedVariable)
        ),
        globalImports = Nil,
        additionalClasses = List(
          classOf[AdditionalClass]
        )
      )

    override def buildInfo(): Map[String, String] = Map()
  }

  object Transformer1 extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(
        @ParamName("param1")
        @AdditionalVariables(value =
          Array(new AdditionalVariable(name = "var1", clazz = classOf[OnlyUsedInAdditionalVariable]))
        )
        someStupidNameWithoutMeaning: LazyParameter[String]
    ): Unit = {}

  }

  object TransformerWithGenericParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("foo") foo: List[String]): Unit = {}

  }

  object TransformerReturningContextTransformationWithOutputVariable extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      ContextTransformation
        .definedBy((in: ValidationContext) => in.withVariable(variableName, Typed[String], None))
        .implementedBy(null)
    }

  }

  object TransformerReturningContextTransformationWithoutOutputVariable extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[Void])
    def invoke(): ContextTransformation = {
      ContextTransformation
        .definedBy(Valid(_))
        .implementedBy(null)
    }

  }

  object TransformerWithBranchParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(
        @BranchParamName("lazyParam") lazyParam: Map[String, LazyParameter[Integer]],
        @BranchParamName("eagerParam") eagerParam: Map[String, Integer]
    ): Unit = {}

  }

  object TransformerWithFixedValueParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(
        @ParamName("param1")
        @SimpleEditor(
          `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
          possibleValues = Array(
            new LabeledExpression(expression = "'foo'", label = "foo"),
            new LabeledExpression(expression = "'bar'", label = "bar")
          )
        )
        someStupidNameWithoutMeaning: String
    ): Unit = {}

  }

  object TransformerWithDefaultValueForParameter extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(
        @ParamName("param1")
        @DefaultValue("'foo'")
        someStupidNameWithoutMeaning: String
    ): Unit = {}

  }

  object TransformerWithOptionalDefaultValueForParameter extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(
        @ParamName("param1")
        @DefaultValue("'foo'")
        @Nullable someStupidNameWithoutMeaning: String
    ): Unit = {}

  }

  case class OnlyUsedInAdditionalVariable(someField: String)

  case class AdditionalClass(someField: String)

  case class EmptyExplicitMethodToInvoke(parameters: List[Parameter], returnType: TypingResult)
      extends EagerServiceWithStaticParametersAndReturnType {

    override def invoke(eagerParameters: Map[ParameterName, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ContextId,
        metaData: MetaData,
        componentUseCase: ComponentUseCase
    ): Future[Any] = ???

  }

  object SampleHelper {
    def identity(value: Any): Any = value
  }

  object SampleTypedVariable extends TypedGlobalVariable {

    val Value = 123

    override def value(metadata: MetaData): Any = Value

    override def returnType(metadata: MetaData): TypingResult = Typed.fromInstance(Value)

    override def initialReturnType: TypingResult = Typed(classOf[Int])
  }

}
