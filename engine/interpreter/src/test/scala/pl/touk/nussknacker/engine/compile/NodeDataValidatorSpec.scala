package pl.touk.nussknacker.engine.compile

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParseError, MissingCustomNodeExecutor, MissingService, MissingSinkFactory, MissingSourceFactory, OverwrittenVariable}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, Service, StreamMetaData, definition}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationPerformed, ValidationResponse}
import pl.touk.nussknacker.engine.compile.validationHelpers.{DynamicParameterJoinTransformer, Enricher, GenericParametersSink, GenericParametersSource, GenericParametersTransformer, SimpleStringService}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Filter, NodeData, Processor, Sink, Source, Variable, VariableBuilder}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.api.typed.typing.TypedObjectTypingResult

class NodeDataValidatorSpec extends FunSuite with Matchers with Inside {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "genericJoin" -> WithCategories(DynamicParameterJoinTransformer),
      "genericTransformer" -> WithCategories(GenericParametersTransformer)
    )

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
      "stringService" -> WithCategories(SimpleStringService)
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "genericParametersSource" -> WithCategories(GenericParametersSource)
    )

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
      "genericParametersSink" -> WithCategories(GenericParametersSink)
    )
  })


  test("should validate sink factory") {
    validate(Sink("tst1", SinkRef("genericParametersSink",
      List(par("par1", "'a,b'"),
        par("lazyPar1", "#aVar + 3"),
        par("a", "'a'"),
        par("b", "'dd'")))), ValidationContext(Map("aVar" -> Typed[Long]))) shouldBe ValidationPerformed(Nil, Some(genericParameters), None)

    inside(validate(Sink("tst1", SinkRef("genericParametersSink",
          List(par("par1", "'a,b'"),
            par("lazyPar1", "#aVar + ''"),
            par("a", "'a'"),
            par("b", "''")))), ValidationContext(Map("aVar" -> Typed[String])))) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, Some(params), _) =>
        params shouldBe genericParameters
        error.message shouldBe "Bad expression type, expected: Long, found: String"
    }

    validate(Sink("tst1", SinkRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_:MissingSinkFactory)::Nil, _, _) =>
    }

  }

  test("should validate source factory") {
    validate(Source("tst1", SourceRef("genericParametersSource",
      List(par("par1", "'a,b'"),
        par("lazyPar1", "11"),
        par("a", "'a'"),
        par("b", "'b'")))), ValidationContext()) shouldBe ValidationPerformed(Nil, Some(genericParameters), None)

    inside(validate(Source("tst1", SourceRef("genericParametersSource",
          List(par("par1", "'a,b'"),
            par("lazyPar1", "''"),
            par("a", "'a'"),
            par("b", "''")))), ValidationContext())) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, _, _) =>
        error.message shouldBe "Bad expression type, expected: Long, found: String"
    }

    validate(Source("tst1", SourceRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_:MissingSourceFactory)::Nil, _, _) =>
    }

  }

  test("should validate filter") {
    inside(validate(Filter("filter", "#a > 3"), ValidationContext(Map("a" -> Typed[String])))) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, None, _) =>
        error.message shouldBe "Wrong part types"
    }
  }

  test("should validate service") {
    inside(validate(node.Enricher("stringService", ServiceRef("stringService", List(par("stringParam", "#a.length + 33"))), "out"),
      ValidationContext(Map("a" -> Typed[String])))) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, None, _) =>
        error.message shouldBe "Bad expression type, expected: String, found: Integer"
    }

    validate(Processor("tst1", ServiceRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_:MissingService)::Nil, _, _) =>
    }
  }

  test("should validate custom node") {
    inside(validate(CustomNode("tst1", Some("out"), "genericTransformer",
          List(par("par1", "'a,b'"),
            par("lazyPar1", "#aVar + ''"),
            par("a", "'a'"),
            par("b", "''"))), ValidationContext(Map("aVar" -> Typed[String])))) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, Some(params), _) =>
        params shouldBe genericParameters
        error.message shouldBe "Bad expression type, expected: Long, found: String"
    }


    validate(CustomNode("tst1", None, "doNotExist", Nil), ValidationContext()) should matchPattern {
      case ValidationPerformed((_:MissingCustomNodeExecutor)::Nil, _, _) =>
    }
  }

  test("should validate variable definition") {
    inside(
      validate(Variable("var1", "var1", "doNotExist", None), ValidationContext(Map.empty))
    ) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, None, _) =>
        error.message shouldBe "Non reference 'doNotExist' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("should not allow to override output variable in variable definition") {
    inside(
      validate(Variable("var1", "var1", "42L", None), ValidationContext(localVariables = Map("var1" -> typing.Unknown)))
    ) {
      case ValidationPerformed(OverwrittenVariable("var1", "var1") :: Nil, None, _) =>
    }
  }

  test("should return expression type info for variable definition") {
    inside(
      validate(Variable("var1", "var1", "42L", None), ValidationContext(Map.empty))
    ) {
      case ValidationPerformed(Nil, _, Some(expressionType)) => expressionType.display shouldBe "Long"
    }
  }

  test("should validate variable builder definition") {
    inside(
      validate(VariableBuilder("var1", "var1", List(Field("field1", "doNotExist")), None), ValidationContext(Map.empty))
    ) {
      case ValidationPerformed((error:ExpressionParseError) :: Nil, None, _) =>
        error.message shouldBe "Non reference 'doNotExist' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("should not allow to override output variable in variable builder definition") {
    inside(
      validate(VariableBuilder("var1", "var1", Nil, None), ValidationContext(localVariables = Map("var1" -> typing.Unknown)))
    ) {
      case ValidationPerformed(OverwrittenVariable("var1", "var1") :: Nil, None, _) =>
    }
  }

  test("should return inferred type for variable builder output") {
     inside(
      validate(VariableBuilder("var1", "var1", List(Field("field1", "42L"), Field("field2", "'some string'")), None), ValidationContext(Map.empty))
    ) {
      case ValidationPerformed(Nil, None, Some(TypedObjectTypingResult(fields, _))) =>
        fields.mapValues(_.display) shouldBe Map("field1" -> "Long", "field2" -> "String")
    }
  }

  ignore("should validate subprocess") {
    //TODO
  }

  ignore("should validate switch") {
    //TODO
  }

  private def genericParameters = List(
    definition.Parameter[String]("par1"),
    definition.Parameter[Long]("lazyPar1").copy(isLazyParameter = true),
    definition.Parameter[Any]("a"),
    definition.Parameter[Any]("b")
  )

  private def validate(nodeData: NodeData, ctx: ValidationContext, branchCtxs: Map[String, ValidationContext] = Map.empty): ValidationResponse = {
    NodeDataValidator.validate(nodeData, modelData, ctx, branchCtxs)(MetaData("id", StreamMetaData()))
  }

  private def par(name: String, expr: String): Parameter = Parameter(name, Expression("spel", expr))

}
