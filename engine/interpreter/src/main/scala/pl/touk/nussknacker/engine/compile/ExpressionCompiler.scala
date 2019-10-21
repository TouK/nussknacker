package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.ValidatedNel
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
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

  def withOptimization(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
    = default(loader, expressionConfig, expressionConfig.optimizeCompilation)

  def withoutOptimization(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata]): ExpressionCompiler
      = default(loader, expressionConfig, optimizeCompilation = false)

  private def default(loader: ClassLoader, expressionConfig: ExpressionDefinition[ObjectMetadata], optimizeCompilation: Boolean): ExpressionCompiler = {
    val defaultParsers = Seq(
      SpelExpressionParser.default(loader, optimizeCompilation, expressionConfig.globalImports, SpelExpressionParser.Standard),
      SpelExpressionParser.default(loader, optimizeCompilation, expressionConfig.globalImports, SpelExpressionParser.Template),
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
    compileObjectParameters(parameters.map(p => Parameter(p.name, Unknown, classOf[Any])), parameters, ctx)

  def compileObjectParameters(parameterDefinitions: List[Parameter],
                              parameters: List[evaluatedparam.Parameter],
                              ctx: ValidationContext)
                             (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {
    compileObjectParameters(parameterDefinitions, parameters, List.empty, ctx, ctx).map(_.map {
      case TypedParameter(name, expr: TypedExpression) => compiledgraph.evaluatedparam.Parameter(name, expr.expression, expr.returnType)
    })
  }

  def compileObjectParameters(parameterDefinitions: List[Parameter],
                              parameters: List[evaluatedparam.Parameter],
                              branchParameters: List[evaluatedparam.BranchParameters],
                              ctx: ValidationContext, ctxForLazyParameters: ValidationContext)
                             (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.TypedParameter]] = {
    val syntax = ValidatedSyntax[PartSubGraphCompilationError]
    import syntax._

    val definedParamNames = parameterDefinitions.map(_.name).toSet
    // TODO JOIN: verify parameter for each branch
    val usedParamNames =  parameters.map(_.name).toSet ++ branchParameters.flatMap(_.parameters).map(_.name)


    Validations.validateParameters(definedParamNames, usedParamNames).andThen { _ =>
      val paramDefMap = parameterDefinitions.map(p => p.name -> p).toMap

      def ctxToUse(pName:String) = if (paramDefMap(pName).isLazyParameter) ctxForLazyParameters else ctx

      val compiledParams = parameters.map { p =>
        compileParam(p, ctxToUse(p.name), paramDefMap(p.name))
      }
      val compiledBranchParams = (for {
        branchParams <- branchParameters
        p <- branchParams.parameters
      } yield p.name -> (branchParams.branchId, p.expression)).toGroupedMap.toList.map {
        case (paramName, branchIdAndExpressions) =>
          //TODO: handle context for branch parameters correctly...
          compileParam(branchIdAndExpressions, ctxForLazyParameters, paramDefMap(paramName))
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

  private def compileParam(branchIdAndExpressions: List[(String, expression.Expression)],
                           ctx: ValidationContext,
                           definition: Parameter)
                          (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    enrichContext(ctx, definition).andThen { finalCtx =>
      branchIdAndExpressions.map {
        case (branchId, expression) =>
          // TODO JOIN: branch id on error field level
          compile(expression, Some(s"${definition.name} for branch $branchId"), finalCtx, Unknown).map(branchId -> _)
      }.sequence.map(exprByBranchId => compiledgraph.evaluatedparam.TypedParameter(definition.name, TypedExpressionMap(exprByBranchId.toMap)))
    }
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

}
