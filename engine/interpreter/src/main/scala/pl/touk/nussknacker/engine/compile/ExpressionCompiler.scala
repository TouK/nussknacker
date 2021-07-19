package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.ValidatedNel
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParser, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectMetadata
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.graph.{evaluatedparam, expression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.sql.SqlExpressionParser
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.{ModelData, TypeDefinitionSet, compiledgraph, graph}

object ExpressionCompiler {

  def withOptimization(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata],
                       settings: ClassExtractionSettings, typeDefinitionSet: TypeDefinitionSet): ExpressionCompiler
  = default(loader, dictRegistry, expressionConfig, expressionConfig.optimizeCompilation, settings, typeDefinitionSet)

  def withoutOptimization(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata],
                          settings: ClassExtractionSettings, typeDefinitionSet: TypeDefinitionSet): ExpressionCompiler
  = default(loader, dictRegistry, expressionConfig, optimizeCompilation = false, settings, typeDefinitionSet)

  def withoutOptimization(modelData: ModelData): ExpressionCompiler = {
    withoutOptimization(modelData.modelClassLoader.classLoader,
      modelData.dictServices.dictRegistry,
      modelData.processDefinition.expressionConfig,
      modelData.processDefinition.settings,
      TypeDefinitionSet(modelData.typeDefinitions))
  }

  private def default(loader: ClassLoader, dictRegistry: DictRegistry, expressionConfig: ExpressionDefinition[ObjectMetadata],
                      optimizeCompilation: Boolean, settings: ClassExtractionSettings, typeDefinitionSet: TypeDefinitionSet): ExpressionCompiler = {
    val defaultParsers = Seq(
      SpelExpressionParser.default(loader, dictRegistry, optimizeCompilation, expressionConfig.strictTypeChecking,
        expressionConfig.globalImports, SpelExpressionParser.Standard, expressionConfig.strictMethodsChecking,
        expressionConfig.staticMethodInvocationsChecking, typeDefinitionSet, expressionConfig.disableMethodExecutionForUnknown)(settings),
      SpelExpressionParser.default(loader, dictRegistry, optimizeCompilation, expressionConfig.strictTypeChecking,
        expressionConfig.globalImports, SpelExpressionParser.Template, expressionConfig.strictMethodsChecking,
        expressionConfig.staticMethodInvocationsChecking, typeDefinitionSet, expressionConfig.disableMethodExecutionForUnknown)(settings),
      SqlExpressionParser)
    val parsersSeq = defaultParsers ++ expressionConfig.languages.expressionParsers
    val parsers = parsersSeq.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers)
  }

}

class ExpressionCompiler(expressionParsers: Map[String, ExpressionParser]) {

  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]

  import syntax._

  //used only for services
  def compileEagerObjectParameters(parameterDefinitions: List[Parameter],
                                   parameters: List[evaluatedparam.Parameter],
                                   ctx: ValidationContext)
                                  (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {
    compileObjectParameters(parameterDefinitions, parameters, List.empty, ctx, Map.empty, eager = true).map(_.map {
      case (TypedParameter(name, expr: TypedExpression), paramDef) =>
        compiledgraph.evaluatedparam.Parameter(expr, paramDef)
      case (TypedParameter(name, expr: TypedExpressionMap), paramDef) => throw new IllegalArgumentException("Typed expression map should not be here...")
    })
  }

  //used by ProcessCompiler
  def compileObjectParameters(parameterDefinitions: List[Parameter],
                              parameters: List[evaluatedparam.Parameter],
                              branchParameters: List[evaluatedparam.BranchParameters],
                              ctx: ValidationContext, branchContexts: Map[String, ValidationContext], eager: Boolean)
                             (implicit nodeId: NodeId)
  : ValidatedNel[PartSubGraphCompilationError, List[(compiledgraph.evaluatedparam.TypedParameter, Parameter)]] = {
    val syntax = ValidatedSyntax[PartSubGraphCompilationError]
    import syntax._

    val allParameters = parameters ++ branchParameters.flatMap(_.parameters)
    Validations.validateParameters(parameterDefinitions, allParameters).andThen { _ =>
      val paramDefMap = parameterDefinitions.map(p => p.name -> p).toMap

      val compiledParams = parameters.map { p =>
        compileParam(p, ctx, paramDefMap(p.name), eager)
      }
      val compiledBranchParams = (for {
        branchParams <- branchParameters
        p <- branchParams.parameters
      } yield p.name -> (branchParams.branchId, p.expression)).toGroupedMap.toList.map {
        case (paramName, branchIdAndExpressions) =>
          compileBranchParam(branchIdAndExpressions, branchContexts, paramDefMap(paramName))
      }
      (compiledParams ++ compiledBranchParams).sequence
        .map(typed => typed.map(t => (t, paramDefMap(t.name))))
    }
  }

  def compileParam(param: graph.evaluatedparam.Parameter,
                   ctx: ValidationContext,
                   definition: Parameter, eager: Boolean)
                  (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.TypedParameter] = {
    val ctxToUse = if (definition.isLazyParameter || eager) ctx else ctx.clearVariables
    enrichContext(ctxToUse, definition).andThen { finalCtx =>
      compile(param.expression, Some(param.name), finalCtx, definition.typ)
        .map(compiledgraph.evaluatedparam.TypedParameter(param.name, _))
    }
  }

  def compileBranchParam(branchIdAndExpressions: List[(String, expression.Expression)],
                         branchContexts: Map[String, ValidationContext],
                         definition: Parameter)
                        (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    branchIdAndExpressions.map {
      case (branchId, expression) =>
        enrichContext(branchContexts(branchId), definition).andThen { finalCtx =>
          // TODO JOIN: branch id on error field level
          compile(expression, Some(s"${definition.name} for branch $branchId"), finalCtx, definition.typ).map(branchId -> _)
        }
    }.sequence.map(exprByBranchId => compiledgraph.evaluatedparam.TypedParameter(definition.name, TypedExpressionMap(exprByBranchId.toMap)))
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
                                      fieldName: String,
                                      expectedType: TypingResult)
                                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language))).toValidatedNel

    validParser andThen { parser =>
      parser.parseWithoutContextValidation(n.expression, expectedType)
        .leftMap(errs => errs.map(err => ProcessCompilationError.ExpressionParseError(err.message, Some(fieldName), n.expression)))
    }
  }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ExpressionCompiler =
    new ExpressionCompiler(expressionParsers.map {
      case (k, v) => k -> modify.lift(v).getOrElse(v)
    })

  private def enrichContext(ctx: ValidationContext,
                            definition: Parameter)
                           (implicit nodeId: NodeId) = {
    val withoutVariablesToHide = ctx.copy(localVariables = ctx.localVariables
      .filterKeys(variableName => !definition.variablesToHide.contains(variableName)))
    definition.additionalVariables.foldLeft[ValidatedNel[PartSubGraphCompilationError, ValidationContext]](Valid(withoutVariablesToHide)) {
      case (acc, (name, typingResult)) => acc.andThen(_.withVariable(name, typingResult, None))
    }
  }

}
