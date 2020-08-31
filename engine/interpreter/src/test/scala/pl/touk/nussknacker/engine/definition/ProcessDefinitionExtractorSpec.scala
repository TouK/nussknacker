package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.{ProcessSignalSender, SignalTransformer}
import pl.touk.nussknacker.engine.api.typed.TypedGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.StandardObjectWithMethodDef

import scala.concurrent.Future

class ProcessDefinitionExtractorSpec extends FunSuite with Matchers {

  private val processDefinition: ProcessDefinitionExtractor.ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef] =
    ProcessDefinitionExtractor.extractObjectWithMethods(TestCreator,
      process.ProcessObjectDependencies(ConfigFactory.load(), DefaultObjectNaming))

  test("extract definitions") {
    val signal1 = processDefinition.signalsWithTransformers.get("signal1")
    signal1 shouldBe 'defined
    signal1.get._2 shouldBe Set("transformer1")
    signal1.get._1.asInstanceOf[StandardObjectWithMethodDef].methodDef.name shouldBe "send1"
  }

  test("extract additional variables info from annotation") {
    val methodDef = processDefinition.customStreamTransformers("transformer1")._1.asInstanceOf[StandardObjectWithMethodDef].methodDef
    val additionalVars = methodDef.orderedDependencies.definedParameters.head.additionalVariables
    additionalVars("var1") shouldBe Typed[OnlyUsedInAdditionalVariable]
  }

  test("extract type info from classes from additional variables") {
    val types = ProcessDefinitionExtractor.extractTypes(processDefinition)
    val classDefinition = types.find(_.clazzName == Typed[OnlyUsedInAdditionalVariable])
      classDefinition.map(_.methods.keys) shouldBe Some(Set("someField", "toString"))
  }

  test("extract definition from WithExplicitMethodToInvoke") {
    val definition = processDefinition.services("configurable1")

    definition.returnType shouldBe Typed[String]
    definition.asInstanceOf[StandardObjectWithMethodDef].methodDef.runtimeClass shouldBe classOf[Future[_]]

    definition.parameters shouldBe List(Parameter[Int]("param1"))
  }

  test("extract definition with generic params") {
    val definition = processDefinition.customStreamTransformers("transformerWithGenericParam")._1

    definition.objectDefinition.parameters should have size 1
    definition.objectDefinition.parameters.head.typ shouldEqual Typed.fromDetailedType[List[String]]
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

  object TestCreator extends ProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "transformer1" -> WithCategories(Transformer1, "cat"),
        "transformerWithGenericParam" -> WithCategories(TransformerWithGenericParam, "cat"),
        "transformerWithBranchParam" -> WithCategories(TransformerWithBranchParam, "cat"))

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
      "configurable1" -> WithCategories(EmptyExplicitMethodToInvoke(List(Parameter[Int]("param1")), Typed[String]), "cat")
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map()

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map()

    override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

    override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
      ExceptionHandlerFactory.noParams(_ => EspExceptionHandler.empty)

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = ExpressionConfig(
      globalProcessVariables = Map(
        "helper" -> WithCategories(SampleHelper, "category"),
        "typedGlobal" -> WithCategories(SampleTypedVariable, "category")
      ),
      globalImports = Nil
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
      param1: String) : Unit = {}
  }

  object TransformerWithGenericParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@ParamName("foo") foo: List[String]): Unit = {
    }

  }

  object TransformerWithBranchParam extends CustomStreamTransformer {

    @MethodToInvoke
    def invoke(@BranchParamName("lazyParam") lazyParam: Map[String, LazyParameter[Integer]],
               @BranchParamName("eagerParam") eagerParam: Map[String, Integer]): Unit = {
    }

  }

  class Signal1 extends ProcessSignalSender {
    @MethodToInvoke
    def send1(@ParamName("param1") param1: String) : Unit = {}
  }

  case class OnlyUsedInAdditionalVariable(someField: String)

  case class EmptyExplicitMethodToInvoke(parameterDefinition: List[Parameter], returnType: TypingResult) extends Service with WithExplicitMethodToInvoke {

    override def runtimeClass: Class[_] = classOf[Future[_]]

    override def additionalDependencies: List[Class[_]] = List()

    override def invoke(params: List[AnyRef]): Future[AnyRef] = ???
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
