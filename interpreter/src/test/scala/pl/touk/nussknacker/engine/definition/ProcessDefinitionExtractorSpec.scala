package pl.touk.nussknacker.engine.definition

import cats.data.Validated.Valid

import java.time.Duration
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{AdditionalVariableProvidedInRuntime, FixedExpressionValue, FixedValuesValidator, MandatoryParameterValidator, Parameter, RegExpParameterValidator}
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.{ProcessSignalSender, SignalTransformer}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{GenericNodeTransformationMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import javax.annotation.Nullable
import scala.concurrent.{ExecutionContext, Future}

class ProcessDefinitionExtractorSpec extends FunSuite with Matchers {

  private val processDefinition: ProcessDefinitionExtractor.ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef] =
    ProcessDefinitionExtractor.extractObjectWithMethods(TestCreator,
      process.ProcessObjectDependencies(ConfigFactory.load(), ObjectNamingProvider(getClass.getClassLoader)))

  test("extract definitions") {
    val signal1 = processDefinition.signalsWithTransformers.get("signal1")
    signal1 shouldBe 'defined
    signal1.get._2 shouldBe Set("transformer1")
    signal1.get._1.asInstanceOf[StandardObjectWithMethodDef].methodDef.name shouldBe "send1"
  }

  test("extract additional variables info from annotation") {
    val methodDef = processDefinition.customStreamTransformers("transformer1")._1.asInstanceOf[StandardObjectWithMethodDef].methodDef
    val additionalVars = methodDef.orderedDependencies.definedParameters.head.additionalVariables
    additionalVars("var1") shouldBe AdditionalVariableProvidedInRuntime[OnlyUsedInAdditionalVariable]
  }

  test("extract type info from classes from additional variables") {
    val types = ProcessDefinitionExtractor.extractTypes(processDefinition)
    val classDefinition = types.find(_.clazzName == Typed[OnlyUsedInAdditionalVariable])
      classDefinition.map(_.methods.keys) shouldBe Some(Set("someField", "toString"))
  }

  test("extract type info from additional classes") {
    val types = ProcessDefinitionExtractor.extractTypes(processDefinition)
    val classDefinition = types.find(_.clazzName == Typed[AdditionalClass])
    classDefinition.map(_.methods.keys) shouldBe Some(Set("someField", "toString"))
  }

  test("extract definition from WithExplicitMethodToInvoke") {
    val definition = processDefinition.services("configurable1")

    definition.asInstanceOf[GenericNodeTransformationMethodDef]
      .obj.asInstanceOf[EagerServiceWithStaticParametersAndReturnType].returnType shouldBe Typed[String]
  }

  test("extract definition with generic params") {
    val definition = processDefinition.customStreamTransformers("transformerWithGenericParam")._1

    definition.objectDefinition.parameters should have size 1
    definition.objectDefinition.parameters.head.typ shouldEqual Typed.fromDetailedType[List[String]]
  }

  test("extract definition using ContextTransformation") {
    processDefinition.customStreamTransformers("transformerReturningContextTransformationWithOutputVariable")._1.objectDefinition.hasNoReturn shouldBe false
    processDefinition.customStreamTransformers("transformerReturningContextTransformationWithoutOutputVariable")._1.objectDefinition.hasNoReturn shouldBe true
  }

  test("extract validators based on editor") {
    val definition = processDefinition.customStreamTransformers("transformerWithFixedValueParam")._1

    definition.objectDefinition.parameters should have size 1
    val parameter = definition.objectDefinition.parameters.head
    parameter.validators should contain (MandatoryParameterValidator)
    parameter.validators should contain (FixedValuesValidator(List(FixedExpressionValue("'foo'", "foo"), FixedExpressionValue("'bar'", "bar"))))
  }

  test("extract default value from annotation") {
    val definition = processDefinition.customStreamTransformers("transformerWithDefaultValueForParameter")._1

    definition.objectDefinition.parameters should have size 1
    val parameter = definition.objectDefinition.parameters.head
    parameter.defaultValue shouldEqual Some("'foo'")
  }

  test("default value from annotation should have higher priority than optionality") {
    val definition = processDefinition.customStreamTransformers("transformerWithOptionalDefaultValueForParameter")._1

    definition.objectDefinition.parameters should have size 1
    val parameter = definition.objectDefinition.parameters.head
    parameter.defaultValue shouldEqual Some("'foo'")
  }

  test("extract definition with branch params") {
    val definition = processDefinition.customStreamTransformers("transformerWithBranchParam")._1

    definition.objectDefinition.parameters should have size 2

    val lazyParam = definition.objectDefinition.parameters.head
    lazyParam.branchParam shouldBe true
    lazyParam.isLazyParameter shouldBe true
    lazyParam.typ shouldEqual Typed[Integer]

    val eagerParam = definition.objectDefinition.parameters.apply(1)
    eagerParam.branchParam shouldBe true
    eagerParam.isLazyParameter shouldBe false
    eagerParam.typ shouldEqual Typed[Integer]
  }

  test("extract basic global variable") {
    val definition = processDefinition.expressionConfig.globalVariables

    val helperDef = definition("helper")
    helperDef.obj shouldBe SampleHelper
    helperDef.returnType shouldBe Typed(SampleHelper.getClass)
  }

  test("extract typed global variable") {
    val definition = processDefinition.expressionConfig.globalVariables

    val typedGlobalDef = definition("typedGlobal")
    typedGlobalDef.obj shouldBe SampleTypedVariable
    typedGlobalDef.returnType shouldBe Typed(classOf[Int])
  }

  test("extracts validators from config") {
    val parameter = processDefinition.customStreamTransformers("transformer1")._1.parameters.find(_.name == "param1")
    parameter.map(_.validators) shouldBe Some(List(MandatoryParameterValidator, RegExpParameterValidator(".*", "has to match...", "really has to match...")))
  }

  object TestCreator extends ProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "transformer1" -> WithCategories(Transformer1, "cat"),
        "transformerWithGenericParam" -> WithCategories(TransformerWithGenericParam, "cat"),
        "transformerReturningContextTransformationWithOutputVariable" -> WithCategories(TransformerReturningContextTransformationWithOutputVariable, "cat"),
        "transformerReturningContextTransformationWithoutOutputVariable" -> WithCategories(TransformerReturningContextTransformationWithoutOutputVariable, "cat"),
        "transformerWithBranchParam" -> WithCategories(TransformerWithBranchParam, "cat"),
        "transformerWithFixedValueParam" -> WithCategories(TransformerWithFixedValueParam, "cat"),
        "transformerWithDefaultValueForParameter" -> WithCategories(TransformerWithDefaultValueForParameter, "cat"),
        "transformerWithOptionalDefaultValueForParameter" -> WithCategories(TransformerWithOptionalDefaultValueForParameter, "cat"))

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
      "configurable1" -> WithCategories(EmptyExplicitMethodToInvoke(
        List(Parameter[Int]("param1"), Parameter[Duration]("durationParam")
      ), Typed[String]), "cat")
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map()

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map()

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = ExpressionConfig(
      globalProcessVariables = Map(
        "helper" -> WithCategories(SampleHelper, "category"),
        "typedGlobal" -> WithCategories(SampleTypedVariable, "category")
      ),
      globalImports = Nil, additionalClasses = List(
        classOf[AdditionalClass]
      )
    )

    override def buildInfo(): Map[String, String] = Map()

    override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = Map(
      "signal1" -> WithCategories(new Signal1, "cat")
    )
  }

  object Transformer1 extends CustomStreamTransformer {

    @MethodToInvoke
    @SignalTransformer(signalClass = classOf[Signal1])
    def invoke(
      @ParamName("param1")
      @AdditionalVariables(value = Array(new AdditionalVariable(name = "var1", clazz = classOf[OnlyUsedInAdditionalVariable])))
      someStupidNameWithoutMeaning: LazyParameter[String]) : Unit = {}
  }

  object TransformerWithGenericParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("foo") foo: List[String]): Unit = {
    }

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
    def invoke(@BranchParamName("lazyParam") lazyParam: Map[String, LazyParameter[Integer]],
               @BranchParamName("eagerParam") eagerParam: Map[String, Integer]): Unit = {
    }

  }

  object TransformerWithFixedValueParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("param1")
               @SimpleEditor(`type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                 possibleValues = Array(
                 new LabeledExpression(expression = "'foo'", label = "foo"),
                 new LabeledExpression(expression = "'bar'", label = "bar")))
               someStupidNameWithoutMeaning: String) : Unit = {}
  }

  object TransformerWithDefaultValueForParameter extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("param1")
               @DefaultValue("'foo'")
               someStupidNameWithoutMeaning: String) : Unit = {}
  }

  object TransformerWithOptionalDefaultValueForParameter extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("param1")
               @DefaultValue("'foo'")
               @Nullable someStupidNameWithoutMeaning: String) : Unit = {}
  }

  class Signal1 extends ProcessSignalSender {
    @MethodToInvoke
    def send1(@ParamName("param1") param1: String) : Unit = {}
  }

  case class OnlyUsedInAdditionalVariable(someField: String)

  case class AdditionalClass(someField: String)

  case class EmptyExplicitMethodToInvoke(parameters: List[Parameter], returnType: TypingResult) extends EagerServiceWithStaticParametersAndReturnType {
    override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                  collector: InvocationCollectors.ServiceInvocationCollector,
                                                  contextId: ContextId, metaData: MetaData): Future[Any] = ???
  }

  object SampleHelper {
    def identity(value: Any): Any = value
  }

  object SampleTypedVariable extends TypedGlobalVariable {
    override def value(metadata: MetaData): Any = ???

    override def returnType(metadata: MetaData): TypingResult = ???

    override def initialReturnType: TypingResult = Typed(classOf[Int])
  }
}
