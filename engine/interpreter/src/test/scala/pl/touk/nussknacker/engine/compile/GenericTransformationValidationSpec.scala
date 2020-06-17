package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, ExpressionParseError, MissingParameters, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedParameter, FailedToDefineParameter, NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, MethodToInvoke, process}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.{api, spel}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class GenericTransformationValidationSpec extends FunSuite with Matchers with OptionValues {

  import spel.Implicits._

  object MyProcessConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "genericParameters" -> WithCategories(GenericParameters)
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "mySource" -> WithCategories(SimpleStringSource))

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink" -> WithCategories(SinkFactory.noParam(new Sink {
        override def testDataOutput: Option[Nothing] = None
      })))

  }

  object SimpleStringSource extends SourceFactory[String] {
    override def clazz: Class[_] = classOf[String]

    @MethodToInvoke
    def create(): api.process.Source[String] = null
  }

  object GenericParameters extends CustomStreamTransformer with SingleInputGenericNodeTransformation[AnyRef] {

    override type State = List[String]

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): GenericParameters.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(initialParameters)
      case TransformationStep(("par1", DefinedEagerParameter(value: String, _))::("lazyPar1", _)::Nil, None) =>
        val split = value.split(",").toList
        NextParameters(split.map(Parameter(_, Unknown, isRequiredParameter = true)), state = Some(split))
      case TransformationStep(("par1", FailedToDefineParameter)::("lazyPar1", _)::Nil, None) =>
        outputParameters(context, dependencies, Nil)
      case TransformationStep(("par1", _)::("lazyPar1", _)::rest, Some(names)) if rest.map(_._1) == names =>
        outputParameters(context, dependencies, rest)
    }

    private def outputParameters(context: ValidationContext, dependencies: List[NodeDependencyValue], rest: List[(String, DefinedParameter)])(implicit nodeId: NodeId): GenericParameters.FinalResults = {
      dependencies.collectFirst { case OutputVariableNameValue(name) => name } match {
        case Some(name) =>
          val result = TypedObjectTypingResult(rest.toMap.mapValues(_.returnType))
          context.withVariable(name, result).fold(
            errors => FinalResults(context, errors.toList),
            FinalResults(_))
        case None =>
          FinalResults(context, errors = List(CustomNodeError("Output not defined", None)))
      }
    }

    override def initialParameters: List[Parameter] = List(
      Parameter[String]("par1"), Parameter[Long]("lazyPar1").copy(isLazyParameter = true)
    )

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef = {
      null
    }

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency, TypedNodeDependency(classOf[MetaData]))


  }

  private val processBase = EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "mySource")
  private val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(MyProcessConfigCreator,
    process.ProcessObjectDependencies(ConfigFactory.empty, DefaultObjectNaming))
  private val validator = ProcessValidator.default(objectWithMethodDef, new SimpleDictRegistry(Map.empty))


  test("should validate happy path") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",

          "par1" -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1" -> "'aa'",
          "val2" -> "11",
          "val3" -> "{false}"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe 'valid
    val info1 = result.typing("end")
    
    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map(
      "val1" -> Typed[String],
      "val2" -> Typed[java.lang.Integer],
      "val3" -> Typed.fromDetailedType[java.util.List[Boolean]]
    ))

  }

  test("should find wrong determining parameter") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "12",
          "lazyPar1" -> "#input == null ? 1 : 5"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(ExpressionParseError("Bad expression type, expected: java.lang.String, found: java.lang.Integer",
      "generic",Some("par1"),"12")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])
  }

  test("should find wrong dependent parameters") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "'val1,val2'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1" -> "''"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(MissingParameters(Set("val2"), "generic")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map(
      "val1" -> Typed[String],
      "val2" -> Unknown
    ))
  }


  test("should find no output variable") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "12",
          "lazyPar1" -> "#input == null ? 1 : 5"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(ExpressionParseError("Bad expression type, expected: java.lang.String, found: java.lang.Integer",
      "generic",Some("par1"),"12")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])
  }


}
