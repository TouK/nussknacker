package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.ValidatedNel
import cats.instances.list._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.expression.{Expression, ExpressionParser, TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{NodeId, ParameterNaming}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.graph._
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

object ExpressionCompiler {

  def withOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition[_],
      classDefinitionSet: ClassDefinitionSet
  ): ExpressionCompiler =
    default(loader, dictRegistry, expressionConfig, expressionConfig.optimizeCompilation, classDefinitionSet)

  def withoutOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition[_],
      classDefinitionSet: ClassDefinitionSet
  ): ExpressionCompiler =
    default(loader, dictRegistry, expressionConfig, optimizeCompilation = false, classDefinitionSet)

  def withoutOptimization(modelData: ModelData): ExpressionCompiler = {
    withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.designerDictServices.dictRegistry,
      modelData.modelDefinition.expressionConfig,
      modelData.modelDefinitionWithClasses.classDefinitions
    )
  }

  private def default(
      classLoader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition[_],
      optimizeCompilation: Boolean,
      classDefinitionSet: ClassDefinitionSet
  ): ExpressionCompiler = {
    def spelParser(flavour: Flavour) =
      SpelExpressionParser.default(
        classLoader,
        expressionConfig,
        dictRegistry,
        optimizeCompilation,
        flavour,
        classDefinitionSet
      )
    val defaultParsers = Seq(spelParser(SpelExpressionParser.Standard), spelParser(SpelExpressionParser.Template))
    val parsersSeq     = defaultParsers ++ expressionConfig.languages.expressionParsers
    val parsers        = parsersSeq.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers)
  }

}

class ExpressionCompiler(expressionParsers: Map[String, ExpressionParser]) {

  // used only for services
  def compileEagerObjectParameters(
      parameterDefinitions: List[Parameter],
      parameters: List[evaluatedparam.Parameter],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[compiledgraph.evaluatedparam.Parameter]] = {
    compileObjectParameters(parameterDefinitions, parameters, List.empty, ctx, Map.empty, eager = true).map(_.map {
      case (TypedParameter(name, expr: TypedExpression), paramDef) =>
        compiledgraph.evaluatedparam.Parameter(expr, paramDef)
      case (TypedParameter(name, expr: TypedExpressionMap), paramDef) =>
        throw new IllegalArgumentException("Typed expression map should not be here...")
    })
  }

  // used by ProcessCompiler
  def compileObjectParameters(
      parameterDefinitions: List[Parameter],
      parameters: List[evaluatedparam.Parameter],
      branchParameters: List[evaluatedparam.BranchParameters],
      ctx: ValidationContext,
      branchContexts: Map[String, ValidationContext],
      eager: Boolean
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[(compiledgraph.evaluatedparam.TypedParameter, Parameter)]] = {

    val redundantMissingValidation = Validations.validateRedundantAndMissingParameters(
      parameterDefinitions,
      parameters ++ branchParameters.flatMap(_.parameters)
    )
    val paramDefMap = parameterDefinitions.map(p => p.name -> p).toMap

    val compiledParams = parameters
      .flatMap { p =>
        val paramDef = paramDefMap.get(p.name)
        paramDef.map(pd => compileParam(p, ctx, pd, eager))
      }
    val compiledBranchParams = (for {
      branchParams <- branchParameters
      p            <- branchParams.parameters
    } yield p.name -> (branchParams.branchId, p.expression)).toGroupedMap.toList.flatMap {
      case (paramName, branchIdAndExpressions) =>
        val paramDef = paramDefMap.get(paramName)
        paramDef.map(pd => compileBranchParam(branchIdAndExpressions, branchContexts, pd))
    }
    val allCompiledParams = (compiledParams ++ compiledBranchParams).sequence
      .map(typed => typed.map(t => (t, paramDefMap(t.name))))
    allCompiledParams
      .andThen(allParams => Validations.validateWithCustomValidators(parameterDefinitions, allParams))
      .combine(redundantMissingValidation.map(_ => List()))
  }

  def compileParam(param: evaluatedparam.Parameter, ctx: ValidationContext, definition: Parameter, eager: Boolean)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.TypedParameter] = {
    val ctxToUse = if (definition.isLazyParameter || eager) ctx else ctx.clearVariables
    enrichContext(ctxToUse, definition).andThen { finalCtx =>
      compile(param.expression, Some(param.name), finalCtx, definition.typ)
        .map(compiledgraph.evaluatedparam.TypedParameter(param.name, _))
    }
  }

  def compileBranchParam(
      branchIdAndExpressions: List[(String, expression.Expression)],
      branchContexts: Map[String, ValidationContext],
      definition: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    branchIdAndExpressions
      .map { case (branchId, expression) =>
        enrichContext(branchContexts(branchId), definition).andThen { finalCtx =>
          // TODO JOIN: branch id on error field level
          compile(
            expression,
            Some(ParameterNaming.getNameForBranchParameter(definition, branchId)),
            finalCtx,
            definition.typ
          ).map(
            branchId -> _
          )
        }
      }
      .sequence
      .map(exprByBranchId =>
        compiledgraph.evaluatedparam.TypedParameter(definition.name, TypedExpressionMap(exprByBranchId.toMap))
      )
  }

  def compile(
      n: expression.Expression,
      fieldName: Option[String],
      validationCtx: ValidationContext,
      expectedType: TypingResult
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedExpression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
      .toValidatedNel

    validParser andThen { parser =>
      parser
        .parse(n.expression, validationCtx, expectedType)
        .leftMap(errs =>
          errs.map(err =>
            ProcessCompilationError.ExpressionParserCompilationError(err.message, fieldName, n.expression)
          )
        )
    }
  }

  def compileWithoutContextValidation(n: expression.Expression, fieldName: String, expectedType: TypingResult)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
      .toValidatedNel

    validParser andThen { parser =>
      parser
        .parseWithoutContextValidation(n.expression, expectedType)
        .leftMap(errs =>
          errs.map(err =>
            ProcessCompilationError.ExpressionParserCompilationError(err.message, Some(fieldName), n.expression)
          )
        )
    }
  }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ExpressionCompiler =
    new ExpressionCompiler(expressionParsers.map { case (k, v) =>
      k -> modify.lift(v).getOrElse(v)
    })

  private def enrichContext(ctx: ValidationContext, definition: Parameter)(implicit nodeId: NodeId) = {
    val withoutVariablesToHide = ctx.copy(localVariables =
      ctx.localVariables
        .filterKeysNow(variableName => !definition.variablesToHide.contains(variableName))
        .toMap
    )
    definition.additionalVariables.foldLeft[ValidatedNel[PartSubGraphCompilationError, ValidationContext]](
      Valid(withoutVariablesToHide)
    ) { case (acc, (name, typingResult)) =>
      acc.andThen(_.withVariable(name, typingResult.typingResult, None))
    }
  }

}
