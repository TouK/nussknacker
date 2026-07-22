package pl.touk.nussknacker.engine.compile

import cats.data.{IorNel, NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{invalidNel, Valid}
import cats.implicits._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{EagerParameterEvaluationResult, JobData, NodeId}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.dict.{DictRegistry, EngineDictRegistry}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler.CompiledNodeParameters
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  MultipleInputBranchesNodeInputValidationContext,
  NodeInputValidationContext,
  ParameterEvaluator,
  SingleInputNodeInputValidationContext
}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.{ExpressionEvaluator, NullExpression}
import pl.touk.nussknacker.engine.expression.parse.{
  ExpressionParser,
  MultipleBranchesTypedValue,
  SingleBranchTypedValue,
  TypedExpression
}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.Expression.Language.DictKeyWithLabel
import pl.touk.nussknacker.engine.language.dictWithLabel.DictKeyWithLabelExpressionParser
import pl.touk.nussknacker.engine.language.json.{JsonParser, JsonTemplateParser}
import pl.touk.nussknacker.engine.language.tabularDataDefinition.TabularDataDefinitionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.util.Try

object ExpressionCompiler {

  final case class CompiledNodeParameters(
      parameters: List[(TypedParameter, Parameter)],
      evaluationResults: Map[ParameterName, EagerParameterEvaluationResult]
  )

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
      modelData.modelClassLoader,
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

    val spelStandardParser = spelParser(SpelExpressionParser.Standard)
    val spelTemplateParser = spelParser(SpelExpressionParser.Template)
    val defaultParsers =
      Seq(
        spelStandardParser,
        spelTemplateParser,
        DictKeyWithLabelExpressionParser,
        TabularDataDefinitionParser,
        JsonParser,
        new JsonTemplateParser(spelTemplateParser = spelTemplateParser, spelParser = spelStandardParser),
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

  // Runtime-evaluator slot is unused here (only compile-time value resolution)
  private lazy val parameterEvaluator = new ParameterEvaluator(expressionEvaluator, expressionEvaluator, this)

  // used only for services and fragments - in places where component is an Executor instead of a factory
  // that creates Executor
  def compileExecutorComponentNodeParameters(
      parameterDefinitions: List[Parameter],
      nodeParameters: List[NodeParameter],
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): IorNel[PartSubGraphCompilationError, List[CompiledParameter]] = {
    compileNodeParameters(
      parameterDefinitions = parameterDefinitions,
      nodeParameters = nodeParameters,
      nodeBranchParameters = List.empty,
      inputContext = inputContext,
      treatEagerParametersAsLazy = true,
      // services and fragments have no earlier parameters-evaluation pass whose results could be reused
      evaluatedParamsResults = Map.empty
    ).flatMap { compiledNodeParams =>
      compiledNodeParams.parameters
        .map {
          case (TypedParameter(_, expr: SingleBranchTypedValue), paramDef) =>
            paramDef.validators
              .map(v => compileValidator(v, paramDef.name, paramDef.typ, inputContext.globalVariables))
              .sequence
              .map(validators => CompiledParameter(expr.typedExpression, paramDef, validators))
          case (TypedParameter(_, _: MultipleBranchesTypedValue), _) =>
            throw new IllegalArgumentException("Typed expression map should not be here...")
        }
        .sequence
        .toIor
    }
  }

  // used for most cases during node compilation - for all components that are factories of Executors
  def compileNodeParameters(
      parameterDefinitions: List[Parameter],
      nodeParameters: List[NodeParameter],
      nodeBranchParameters: List[BranchParameters],
      inputContext: NodeInputValidationContext,
      treatEagerParametersAsLazy: Boolean = false,
      evaluatedParamsResults: Map[ParameterName, EagerParameterEvaluationResult]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): IorNel[PartSubGraphCompilationError, ExpressionCompiler.CompiledNodeParameters] = {
    def compileParameters(parameterByName: Map[ParameterName, NodeParameter]) = {
      val adjustedParameters = NodeParametersAdjuster.adjustNonBranchParameters(
        parameterDefinitions,
        parameterByName
      )
      val paramDefMap = parameterDefinitions.map(p => p.name -> p).toMap

      val nonBranchParamValidationContext = inputContext match {
        case SingleInputNodeInputValidationContext(validationContext) => validationContext
        case MultipleInputBranchesNodeInputValidationContext(_, validationContextWithGlobalVariablesOnly) =>
          validationContextWithGlobalVariablesOnly
      }
      val compiledParams = adjustedParameters
        .flatMap { nodeParam =>
          paramDefMap
            .get(nodeParam.name)
            .map(paramDef =>
              compileParam(nodeParam, nonBranchParamValidationContext, paramDef, treatEagerParametersAsLazy)
                .map((_, paramDef))
            )
        }

      lazy val branchContexts = inputContext match {
        case MultipleInputBranchesNodeInputValidationContext(validationContextByBranchId, _) =>
          validationContextByBranchId
        case single: SingleInputNodeInputValidationContext =>
          throw new IllegalStateException(
            s"[$single] found in place where MultipleInputBranchesNodeInputValidationContext expected"
          )
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
      allCompiledParams
    }

    // Evaluates the parameters against the dummy context so custom validators can inspect values the typer
    // can't determine statically (e.g. `T(...).parse('...')`). Skips params whose result nothing would use:
    // literals (the typer already knows their value), params with no validators, and params whose expression
    // reads context variables (see `isContextFree`) - those are validated only at runtime.
    def evaluateParamsForCompileTimeValidation(
        compiledParams: List[(TypedParameter, Parameter)]
    ): Map[ParameterName, EagerParameterEvaluationResult] =
      compiledParams.flatMap { case (typedParam, paramDef) =>
        def literalValueNotSet: Boolean = typedParam.typedValue match {
          case single: SingleBranchTypedValue =>
            single.typedExpression.returnType.valueOpt.isEmpty
          case MultipleBranchesTypedValue(valueByBranchId) =>
            valueByBranchId.values.exists(_.typedExpression.returnType.valueOpt.isEmpty)
        }

        def safelyEvaluable: Boolean = typedParam.typedValue match {
          case single: SingleBranchTypedValue =>
            (!paramDef.isLazyParameter && !treatEagerParametersAsLazy) || isContextFree(single, paramDef)
          case MultipleBranchesTypedValue(valueByBranchId) =>
            // Branches whose value the typer already knows skip the (costly) `isContextFree` check - a value the
            // typer could fold is a literal, so it can't read the context and is safe to evaluate as-is.
            valueByBranchId.values.forall(value =>
              value.typedExpression.returnType.valueOpt.isDefined || isContextFree(value, paramDef)
            )
        }

        evaluatedParamsResults.get(typedParam.name) match {
          case Some(alreadyEvaluated) =>
            Some(typedParam.name -> alreadyEvaluated)
          case None if paramDef.validators.nonEmpty && literalValueNotSet && safelyEvaluable =>
            // evaluateEagerParameter evaluates by value, ignoring isLazyParameter - it works for lazy params too.
            // Evaluation may throw (e.g. a runtime error in the expression); such params are skipped - the value
            // simply isn't handed to validators and compilation is not aborted.
            Try(parameterEvaluator.evaluateEagerParameter(typedParam, paramDef)).toOption.map(typedParam.name -> _)
          case None =>
            None
        }

      }.toMap

    // Checks that the expression doesn't read context variables (absent from the dummy context, they would resolve
    // to null) by recompiling it against a context with only globals and fixed-value additional variables.
    def isContextFree(single: SingleBranchTypedValue, paramDef: Parameter): Boolean = {
      val fixedValueAdditionalVariables = paramDef.additionalVariables.collect {
        case (name, additionalVariable: AdditionalVariableWithFixedValue) => name -> additionalVariable.typingResult
      }

      val contextFreeCtx =
        single.expressionInputValidationContext.clearVariables.copy(localVariables = fixedValueAdditionalVariables)

      val rawExpression = single.typedExpression.expression.toExpression

      compile(rawExpression, Some(paramDef.name), contextFreeCtx, paramDef.typ).isValid
    }

    for {
      parameterByName <- nodeParameters
        .map(param => (param.name, param))
        .toMapCheckingDuplicates
        .leftMap(duplicatedKeys => NonEmptyList.of(DuplicatedParameters(duplicatedKeys.toList.toSet, nodeId.id)))
        .toIor
      compiledParams <- compileParameters(parameterByName).toIor
      paramValidatorsMap     = parameterValidatorsMap(parameterDefinitions, inputContext.globalVariables)
      evaluatedParamsResults = evaluateParamsForCompileTimeValidation(compiledParams)
      customValidatorsResult =
        CompileTimeParameterValidation.validateWithCustomValidators(
          compiledParams,
          paramValidatorsMap,
          evaluatedParamsResults
        )
      // We want to accumulate errors from custom validators, but also preserve typing information from allCompiledParams
      // even if custom validators return some errors
      _ <- customValidatorsResult.toIor.addRight(())
    } yield CompiledNodeParameters(compiledParams, evaluatedParamsResults)
  }

  private def parameterValidatorsMap(parameterDefinitions: List[Parameter], globalVariables: Map[String, TypingResult])(
      implicit nodeId: NodeId,
      jobData: JobData
  ): Map[ParameterName, ValidatedNel[PartSubGraphCompilationError, List[Validator]]] =
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

    substituteDictKeyExpression(nodeParam.expression, definition.editors, nodeParam.name).andThen { finalExpr =>
      enrichContext(ctxToUse, definition).andThen { finalCtx =>
        compile(finalExpr, Some(nodeParam.name), finalCtx, definition.typ)
          .map(typedExpression => TypedParameter(nodeParam.name, SingleBranchTypedValue(typedExpression, finalCtx)))
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
        substituteDictKeyExpression(expression, definition.editors, paramName).andThen { finalExpr =>
          enrichContext(branchContexts(branchId), definition).andThen { finalCtx =>
            // TODO JOIN: branch id on error field level
            compile(finalExpr, Some(paramName), finalCtx, definition.typ).map(typedExpression =>
              branchId -> SingleBranchTypedValue(typedExpression, finalCtx)
            )
          }
        }
      }
      .sequence
      .map(exprByBranchId => TypedParameter(definition.name, MultipleBranchesTypedValue(exprByBranchId.toMap)))
  }

  private def substituteDictKeyExpression(
      expression: Expression,
      editors: List[ParameterEditor],
      paramName: ParameterName
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Expression] = {
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

    def isDictKeyWithLabel(expression: Expression): Boolean =
      expression.language == DictKeyWithLabel

    val incompatibleChangeToParameterDefinitionDetected: ValidatedNel[PartSubGraphCompilationError, Expression] =
      invalidNel(IncompatibleParameterDefinitionModification(paramName, expression.language, editors, nodeId.id))

    def validateAndSubstitute(expression: Expression): ValidatedNel[PartSubGraphCompilationError, Expression] = {
      editors match {
        case DictParameterEditor(dictId) :: Nil if isDictKeyWithLabel(expression) =>
          if (expression.expression.isBlank) Valid(expression) else substitute(dictId)
        case DictParameterEditor(dictId) :: _ :: Nil if isDictKeyWithLabel(expression) =>
          if (expression.expression.isBlank) Valid(expression) else substitute(dictId)
        case _ :: DictParameterEditor(dictId) :: Nil if isDictKeyWithLabel(expression) =>
          if (expression.expression.isBlank) Valid(expression) else substitute(dictId)
        case DictParameterEditor(_) :: Nil if !isDictKeyWithLabel(expression) =>
          incompatibleChangeToParameterDefinitionDetected
        case _ if isDictKeyWithLabel(expression) => incompatibleChangeToParameterDefinitionDetected
        case _                                   => Valid(expression)
      }
    }
    validateAndSubstitute(expression)
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
      case l: CustomParameterValidatorLoader => Valid(l.resolved)
      case v                                 => Valid(v)
    }

  def compileValidatorsOrThrow(
      definition: Parameter,
      globalVariables: Map[String, TypingResult]
  )(implicit nodeId: NodeId, jobData: JobData): List[Validator] =
    definition.validators
      .map(v => compileValidator(v, definition.name, definition.typ, globalVariables))
      .sequence
      .valueOr(errors =>
        throw new IllegalStateException(
          s"Validator for '${definition.name.value}' failed compile during runtime preparation — should have been caught earlier: ${errors.toList.mkString(", ")}"
        )
      )

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
      .toRight(NotSupportedExpressionLanguage(n.language))
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

  def withExpressionParsers(
      modify: Map[Language, ExpressionParser] => Map[Language, ExpressionParser]
  ): ExpressionCompiler = {
    new ExpressionCompiler(modify(expressionParsers), dictRegistry, expressionEvaluator)
  }

}
