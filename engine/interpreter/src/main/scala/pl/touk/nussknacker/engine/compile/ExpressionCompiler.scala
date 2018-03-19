package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParser
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectMetadata, Parameter}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.{compiledgraph, graph}

object ExpressionCompiler {

  def withOptimization(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
    = default(loader, expressionConfig, expressionConfig.optimizeCompilation)

  def withoutOptimization(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
      = default(loader, expressionConfig, optimizeCompilation = false)

  private def default(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata], optimizeCompilation: Boolean): ExpressionCompiler = {
    val parsersSeq = Seq(SpelExpressionParser.default(loader, optimizeCompilation, expressionConfig.globalImports))
    val parsers = parsersSeq.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers, expressionConfig.globalVariables.mapValues(_.returnType))
  }

}

class ExpressionCompiler(expressionParsers: Map[String, ExpressionParser],
                         globalVariables: Map[String, TypingResult]) {

  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]

  import syntax._

  def compileValidatedObjectParameters(parameters: List[evaluatedparam.Parameter], ctx: Option[ValidationContext])(implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] =
    compileObjectParameters(parameters.map(p => Parameter(p.name, ClazzRef[Any], ClazzRef[Any])), parameters, ctx)

  def compileObjectParameters(parameterDefinitions: List[Parameter], parameters: List[evaluatedparam.Parameter], ctx: Option[ValidationContext])(implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {
    validateObjectParameters(parameterDefinitions, parameters.map(_.name)).andThen { _ =>
      val paramMap = parameterDefinitions.map(p => p.name -> p.typ).toMap
      parameters.map(p => compileParam(p, ctx, paramMap(p.name))).sequence
    }
  }


  private def validateObjectParameters(parameterDefinitions: List[Parameter], usedParamNames: List[String])
                                      (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    Validations.validateParameters(parameterDefinitions.map(_.name), usedParamNames)
  }

  private def compileParam(n: graph.evaluatedparam.Parameter,
                           ctx: Option[ValidationContext],
                           expectedType: ClazzRef,
                           skipContextValidation: Boolean = false)
                          (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.Parameter] =
    compile(n.expression, Some(n.name), ctx, expectedType)
      .map(typed => compiledgraph.evaluatedparam.Parameter(n.name, typed._2))


  def compile(n: graph.expression.Expression,
              fieldName: Option[String],
              maybeValidationCtx: Option[ValidationContext],
              expectedType: ClazzRef)
             (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, (TypingResult, compiledgraph.expression.Expression)] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language))).toValidatedNel

    //TODO: make it nicer..
    validParser andThen { parser =>
      maybeValidationCtx match {
        case None =>
          parser.parseWithoutContextValidation(n.expression, expectedType).map((Unknown, _))
            .leftMap(err => NonEmptyList.of[PartSubGraphCompilationError](ExpressionParseError(err.message, fieldName, n.expression)))
        case Some(ctx) =>
          val ctxWithGlobalVars = globalVariables.foldLeft[ValidatedNel[PartSubGraphCompilationError, ValidationContext]](Valid(ctx)) { case (acc, (k, v)) =>
            acc.andThen(_.withVariable(k, v))
          }
          ctxWithGlobalVars.andThen(valid =>
            parser.parse(n.expression, valid, expectedType)
              .leftMap(errs => errs.map[PartSubGraphCompilationError](err => ExpressionParseError(err.message, fieldName, n.expression))))
      }
    }
  }
}
