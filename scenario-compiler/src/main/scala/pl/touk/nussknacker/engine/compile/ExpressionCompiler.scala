package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid, invalid, invalidNel, valid}
import cats.data.{Ior, IorNel, NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.dict.{DictRegistry, EngineDictRegistry}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.{ExpressionEvaluator, NullExpression}
import pl.touk.nussknacker.engine.expression.parse.{
  CompiledExpression,
  ExpressionParser,
  TypedExpression,
  TypedExpressionMap
}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.language.dictWithLabel.DictKeyWithLabelExpressionParser
import pl.touk.nussknacker.engine.language.tabularDataDefinition.TabularDataDefinitionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

object ExpressionCompiler {

  def withOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition,
      classDefinitionSet: ClassDefinitionSet,
      expressionEvaluator: ExpressionEvaluator
  ): ExpressionCompiler =
    default(
      loader,
      dictRegistry,
      expressionConfig,
      expressionConfig.optimizeCompilation,
      classDefinitionSet,
      expressionEvaluator
    )

  def withoutOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition,
      classDefinitionSet: ClassDefinitionSet,
      expressionEvaluator: ExpressionEvaluator
  ): ExpressionCompiler =
    default(
      loader,
      dictRegistry,
      expressionConfig,
      optimizeCompilation = false,
      classDefinitionSet,
      expressionEvaluator
    )

  def withoutOptimization(modelData: ModelData): ExpressionCompiler = {
    withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.designerDictServices.dictRegistry,
      modelData.modelDefinition.expressionConfig,
      modelData.modelDefinitionWithClasses.classDefinitions,
      ExpressionEvaluator.unOptimizedEvaluator(
        GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
      )
    )
  }

  private def default(
      classLoader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition,
      optimizeCompilation: Boolean,
      classDefinitionSet: ClassDefinitionSet,
      expressionEvaluator: ExpressionEvaluator
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

    val defaultParsers =
      Seq(
        spelParser(SpelExpressionParser.Standard),
        spelParser(SpelExpressionParser.Template),
        DictKeyWithLabelExpressionParser,
        TabularDataDefinitionParser
      )
    val parsers = defaultParsers.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers, dictRegistry, expressionEvaluator)
  }

}

class ExpressionCompiler(
    expressionParsers: Map[Language, ExpressionParser],
    dictRegistry: DictRegistry,
    expressionEvaluator: ExpressionEvaluator
) {

  // used only for services and fragments - in places where component is an Executor instead of a factory
  // that creates Executor
  def compileExecutorComponentNodeParameters(
      parameterDefinitions: List[Parameter],
      nodeParameters: List[NodeParameter],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): IorNel[PartSubGraphCompilationError, List[CompiledParameter]] = {
    compileNodeParameters(
      parameterDefinitions,
      nodeParameters,
      List.empty,
      ctx,
      Map.empty,
      treatEagerParametersAsLazy = true
    ).map(_.map {
      case (TypedParameter(_, expr: TypedExpression), paramDef) =>
        CompiledParameter(expr, paramDef)
      case (TypedParameter(_, _: TypedExpressionMap), _) =>
        throw new IllegalArgumentException("Typed expression map should not be here...")
    })
  }

  // used for most cases during node compilation - for all components that are factories of Executors
  def compileNodeParameters(
      parameterDefinitions: List[Parameter],
      nodeParameters: List[NodeParameter],
      nodeBranchParameters: List[BranchParameters],
      ctx: ValidationContext,
      branchContexts: Map[String, ValidationContext],
      treatEagerParametersAsLazy: Boolean = false
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): IorNel[PartSubGraphCompilationError, List[(TypedParameter, Parameter)]] = {

    val redundantMissingValidation = Validations.validateRedundantAndMissingParameters(
      parameterDefinitions,
      nodeParameters ++ nodeBranchParameters.flatMap(_.parameters)
    )
    val paramValidatorsMap = parameterValidatorsMap(parameterDefinitions, ctx.globalVariables)
    val paramDefMap        = parameterDefinitions.map(p => p.name -> p).toMap

    val compiledParams = nodeParameters
      .flatMap { nodeParam =>
        paramDefMap
          .get(nodeParam.name)
          .map(paramDef => compileParam(nodeParam, ctx, paramDef, treatEagerParametersAsLazy).map((_, paramDef)))
      }
    val compiledBranchParams = (for {
      branchParams <- nodeBranchParameters
      p            <- branchParams.parameters
    } yield p.name -> (branchParams.branchId, p.expression)).toGroupedMap.toList.flatMap {
      case (paramName, branchIdAndExpressions) =>
        paramDefMap
          .get(paramName)
          .map(paramDef => compileBranchParam(branchIdAndExpressions, branchContexts, paramDef).map((_, paramDef)))
    }
    val allCompiledParams = (compiledParams ++ compiledBranchParams).sequence

    for {
      compiledParams <- allCompiledParams.toIor
      paramsAfterValidation = Validations.validateWithCustomValidators(compiledParams, paramValidatorsMap) match {
        case Valid(a) => Ior.right(a)
        // We want to preserve typing information from allCompiledParams even if custom validators give us some errors
        case Invalid(e) => Ior.both(e, compiledParams)
      }
      combinedParams <- redundantMissingValidation.map(_ => List()).toIor.combine(paramsAfterValidation)
    } yield combinedParams
  }

  private def parameterValidatorsMap(parameterDefinitions: List[Parameter], globalVariables: Map[String, TypingResult])(
      implicit nodeId: NodeId,
      jobData: JobData
  ) =
    parameterDefinitions
      .map(p => p.name -> p.validators.map { v => compileValidator(v, p.name, p.typ, globalVariables) }.sequence)
      .toMap

  def compileParam(
      nodeParam: NodeParameter,
      ctx: ValidationContext,
      definition: Parameter,
      treatEagerParametersAsLazy: Boolean = false
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    val ctxToUse = if (definition.isLazyParameter || treatEagerParametersAsLazy) ctx else ctx.clearVariables

    substituteDictKeyExpression(nodeParam.expression, definition.editor, nodeParam.name).andThen { finalExpr =>
      enrichContext(ctxToUse, definition).andThen { finalCtx =>
        compile(finalExpr, Some(nodeParam.name), finalCtx, definition.typ)
          .map(TypedParameter(nodeParam.name, _))
      }
    }
  }

  def compileBranchParam(
      branchIdAndExpressions: List[(String, Expression)],
      branchContexts: Map[String, ValidationContext],
      definition: Parameter
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, TypedParameter] = {
    branchIdAndExpressions
      .map { case (branchId, expression) =>
        val paramName = definition.name.withBranchId(branchId)
        substituteDictKeyExpression(expression, definition.editor, paramName).andThen { finalExpr =>
          enrichContext(branchContexts(branchId), definition).andThen { finalCtx =>
            // TODO JOIN: branch id on error field level
            compile(finalExpr, Some(paramName), finalCtx, definition.typ).map(branchId -> _)
          }
        }
      }
      .sequence
      .map(exprByBranchId => TypedParameter(definition.name, TypedExpressionMap(exprByBranchId.toMap)))
  }

  private def substituteDictKeyExpression(
      expression: Expression,
      editor: Option[ParameterEditor],
      paramName: ParameterName
  )(
      implicit nodeId: NodeId
  ) = {
    def substitute(dictId: String) = {
      DictKeyWithLabelExpressionParser
        .parseDictKeyWithLabelExpression(expression.expression)
        .leftMap(errs => errs.map(_.toProcessCompilationError(nodeId.id, paramName)))
        .andThen(expr =>
          dictRegistry match {
            case _: EngineDictRegistry =>
              // no need to validate and resolve label it on Engine side, this allows EngineDictRegistry to be lighter (not having to contain dictionaries only used by DictParameterEditor)
              Valid(expression)
            case _ =>
              dictRegistry
                .labelByKey(dictId, expr.key)
                .leftMap(e => NonEmptyList.of(e.toPartSubGraphCompilationError(nodeId.id, paramName)))
                .andThen {
                  case Some(label) => Valid(Expression.dictKeyWithLabel(expr.key, Some(label)))
                  case None        => invalidNel(DictLabelByKeyResolutionFailed(dictId, expr.key, nodeId.id, paramName))
                }
          }
        )
    }

    if (expression.language == Language.DictKeyWithLabel && !expression.expression.isBlank)
      editor match {
        case Some(DictParameterEditor(dictId)) => substitute(dictId)
        case Some(DualParameterEditor(DictParameterEditor(dictId), _)) =>
          substitute(dictId) // in `RAW` mode, expression.language is SpEL, and no substitution/validation is done
        case editor =>
          throw new IllegalStateException(
            s"DictKeyWithLabel expression can only be used with DictParameterEditor, got $editor"
          )
      }
    else
      Valid(expression)
  }

  def compileValidator(
      validator: Validator,
      paramName: ParameterName,
      paramType: TypingResult,
      globalVariables: Map[String, TypingResult]
  )(implicit nodeId: NodeId, jobData: JobData): ValidatedNel[PartSubGraphCompilationError, Validator] =
    validator match {
      case v: ValidationExpressionParameterValidatorToCompile =>
        compileValidationExpressionParameterValidator(
          v,
          paramName,
          paramType,
          globalVariables
        )
      case v => Valid(v)
    }

  private def compileValidationExpressionParameterValidator(
      toCompileValidator: ValidationExpressionParameterValidatorToCompile,
      paramName: ParameterName,
      paramType: TypingResult,
      globalVariables: Map[String, TypingResult]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Validated[NonEmptyList[PartSubGraphCompilationError], ValidationExpressionParameterValidator] =
    compile(
      toCompileValidator.validationExpression,
      paramName = Some(paramName),
      validationCtx = ValidationContext(
        // TODO in the future, we'd like to support more references, see ValidationExpressionParameterValidator
        Map(ValidationExpressionParameterValidator.variableName -> paramType) ++ globalVariables
      ),
      expectedType = Typed[Boolean]
    ).leftMap(_.map {
      case e: ExpressionParserCompilationError =>
        InvalidValidationExpression(
          e.message,
          nodeId.id,
          paramName,
          e.originalExpr
        )
      case e => e
    }).andThen {
      _.expression match {
        case _: NullExpression =>
          invalidNel(
            InvalidValidationExpression(
              "Validation expression cannot be blank",
              nodeId.id,
              paramName,
              toCompileValidator.validationExpression.expression
            )
          )
        case expression =>
          Valid(
            ValidationExpressionParameterValidator(
              expression,
              toCompileValidator.validationFailedMessage,
              expressionEvaluator,
              jobData
            )
          )
      }
    }

  def compile(
      n: Expression,
      paramName: Option[ParameterName],
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
            ProcessCompilationError.ExpressionParserCompilationError(err.message, paramName, n.expression, err.details)
          )
        )
    }
  }

  def compileWithoutContextValidation(n: Expression, paramName: ParameterName, expectedType: TypingResult)(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, CompiledExpression] = {
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
            ProcessCompilationError
              .ExpressionParserCompilationError(err.message, Some(paramName), n.expression, err.details)
          )
        )
    }
  }

  def withLabelsDictTyper: ExpressionCompiler =
    new ExpressionCompiler(
      expressionParsers.map {
        case (k, spel: SpelExpressionParser) => k -> spel.typingDictLabels
        case other                           => other
      },
      dictRegistry,
      expressionEvaluator
    )

  private def enrichContext(ctx: ValidationContext, definition: Parameter)(implicit nodeId: NodeId) = {
    val withoutVariablesToHide = ctx.copy(localVariables =
      ctx.localVariables
        .filterKeysNow(variableName => !definition.variablesToHide.contains(variableName))
    )
    definition.additionalVariables.foldLeft[ValidatedNel[PartSubGraphCompilationError, ValidationContext]](
      Valid(withoutVariablesToHide)
    ) { case (acc, (name, typingResult)) =>
      acc.andThen(_.withVariable(name, typingResult.typingResult, None))
    }
  }

}
