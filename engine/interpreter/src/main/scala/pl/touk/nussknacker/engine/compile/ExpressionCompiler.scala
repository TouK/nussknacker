package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.ValidatedNel
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParser, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectMetadata
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.graph.{evaluatedparam, expression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.sql.SqlExpressionParser
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.{compiledgraph, graph}

object ExpressionCompiler {

  def withOptimization(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
    = default(loader, dictRegistry, expressionConfig, expressionConfig.optimizeCompilation)

  def withoutOptimization(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
      = default(loader, dictRegistry, expressionConfig, optimizeCompilation = false)

  private def default(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata], optimizeCompilation: Boolean): ExpressionCompiler = {
    val defaultParsers = Seq(
      SpelExpressionParser.default(loader, dictRegistry, optimizeCompilation, expressionConfig.strictTypeChecking, expressionConfig.globalImports, SpelExpressionParser.Standard, expressionConfig.strictMethodsChecking),
      SpelExpressionParser.default(loader, dictRegistry, optimizeCompilation, expressionConfig.strictTypeChecking, expressionConfig.globalImports, SpelExpressionParser.Template, expressionConfig.strictMethodsChecking),
      SqlExpressionParser)
    val parsersSeq = defaultParsers  ++ expressionConfig.languages.expressionParsers
    val parsers = parsersSeq.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers)
  }

}

class ExpressionCompiler(expressionParsers: Map[String, ExpressionParser]) {

  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]
  import syntax._

  def compileValidatedObjectParameters(parameters: List[evaluatedparam.Parameter],
                                       ctx: ValidationContext)(implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] =
    compileEagerObjectParameters(parameters.map(p => Parameter.optional(p.name, Unknown, classOf[Any])), parameters, ctx)

  def compileEagerObjectParameters(parameterDefinitions: List[Parameter],
                                   parameters: List[evaluatedparam.Parameter],
                                   ctx: ValidationContext)
                                  (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {
    compileObjectParameters(parameterDefinitions, parameters, List.empty, ctx, Map.empty, eager = true).map(_.map {
      case TypedParameter(name, expr: TypedExpression) => compiledgraph.evaluatedparam.Parameter(name, expr.expression, expr.returnType, expr.typingInfo)
      case TypedParameter(name, expr: TypedExpressionMap) => throw new IllegalArgumentException("Typed expression map should not be here...")
    })
  }

  def compileObjectParameters(parameterDefinitions: List[Parameter],
                              parameters: List[evaluatedparam.Parameter],
                              branchParameters: List[evaluatedparam.BranchParameters],
                              ctx: ValidationContext, branchContexts: Map[String, ValidationContext], eager: Boolean)
                             (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.TypedParameter]] = {
    val syntax = ValidatedSyntax[PartSubGraphCompilationError]
    import syntax._

    val definedParamNames = parameterDefinitions.map(_.name).toSet
    // TODO JOIN: verify parameter for each branch
    val usedParamNames =  parameters.map(_.name).toSet ++ branchParameters.flatMap(_.parameters).map(_.name)


    Validations.validateParameters(definedParamNames, usedParamNames).andThen { _ =>
      val paramDefMap = parameterDefinitions.map(p => p.name -> p).toMap

      def ctxToUse(pName:String): ValidationContext = if (paramDefMap(pName).isLazyParameter || eager) ctx else ctx.clearVariables

      val compiledParams = parameters.map { p =>
        compileParam(p, ctxToUse(p.name), paramDefMap(p.name))
      }
      val compiledBranchParams = (for {
        branchParams <- branchParameters
        p <- branchParams.parameters
      } yield p.name -> (branchParams.branchId, p.expression)).toGroupedMap.toList.map {
        case (paramName, branchIdAndExpressions) =>
          compileBranchParam(branchIdAndExpressions, branchContexts, paramDefMap(paramName))
      }
      (compiledParams ++ compiledBranchParams).sequence
    }
  }

  private def compileParam(param: graph.evaluatedparam.Parameter,
                           ctx: ValidationContext,
                           definition: Parameter)
                          (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.TypedParameter] = {
    enrichContext(ctx, definition).andThen { finalCtx =>
      compile(param.expression, Some(param.name), finalCtx, definition.typ)
        .map(compiledgraph.evaluatedparam.TypedParameter(param.name, _))
    }
  }

  private def compileBranchParam(branchIdAndExpressions: List[(String, expression.Expression)],
                                 branchContexts: Map[String, ValidationContext],
                                 definition: Parameter)
                                (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    branchIdAndExpressions.map {
      case (branchId, expression) =>
        enrichContext(branchContexts(branchId), definition).andThen { finalCtx =>
          // TODO JOIN: branch id on error field level
          compile(expression, Some(s"${definition.name} for branch $branchId"), finalCtx, Unknown).map(branchId -> _)
        }
    }.sequence.map(exprByBranchId => compiledgraph.evaluatedparam.TypedParameter(definition.name, TypedExpressionMap(exprByBranchId.toMap)))
  }

  private def enrichContext(ctx: ValidationContext,
                            definition: Parameter)
                           (implicit nodeId: NodeId) = {
    definition.additionalVariables.foldLeft[ValidatedNel[PartSubGraphCompilationError, ValidationContext]](Valid(ctx)) {
      case (acc, (name, typingResult)) => acc.andThen(_.withVariable(name, typingResult))
    }
  }

  def compile(n: graph.expression.Expression,
              fieldName: Option[String],
              validationCtx: ValidationContext,
              expectedType: TypingResult)
             (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedExpression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language))).toValidatedNel

    validParser andThen { parser =>
      parser.parse(n.expression, validationCtx, expectedType).leftMap(errs => errs.map(err => ProcessCompilationError.ExpressionParseError(err.message, fieldName, n.expression)))
    }
  }

  def compileWithoutContextValidation(n: graph.expression.Expression,
                                      fieldName: String)
                                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language))).toValidatedNel

    validParser andThen { parser =>
      parser.parseWithoutContextValidation(n.expression)
        .leftMap(errs => errs.map(err => ProcessCompilationError.ExpressionParseError(err.message, Some(fieldName), n.expression)))
    }
  }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ExpressionCompiler =
    new ExpressionCompiler(expressionParsers.map {
      case (k, v) => k -> modify.lift(v).getOrElse(v)
    })

}
