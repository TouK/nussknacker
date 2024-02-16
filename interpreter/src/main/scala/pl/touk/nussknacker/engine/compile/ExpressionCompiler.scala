package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Valid, invalid, invalidNel, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{
  DictParameterEditor,
  Parameter,
  ParameterEditor,
  ValidationExpressionParameterValidatorToCompile,
  Validator
}
import pl.touk.nussknacker.engine.api.dict.{DictRegistry, EngineDictRegistry}
import pl.touk.nussknacker.engine.api.expression.{
  Expression => CompiledExpression,
  ExpressionParser,
  TypedExpression,
  TypedExpressionMap
}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.expression.{
  Expression => CompiledExpression,
  ExpressionParser,
  TypedExpression,
  TypedExpressionMap
}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{NodeId, ParameterNaming}
import pl.touk.nussknacker.engine.compiledgraph.{CompiledParameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.spel.parser.DictKeyWithLabelExpressionParser
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

object ExpressionCompiler {

  def withOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition,
      classDefinitionSet: ClassDefinitionSet
  ): ExpressionCompiler =
    default(loader, dictRegistry, expressionConfig, expressionConfig.optimizeCompilation, classDefinitionSet)

  def withoutOptimization(
      loader: ClassLoader,
      dictRegistry: DictRegistry,
      expressionConfig: ExpressionConfigDefinition,
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
      expressionConfig: ExpressionConfigDefinition,
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

    val defaultParsers =
      Seq(
        spelParser(SpelExpressionParser.Standard),
        spelParser(SpelExpressionParser.Template),
        DictKeyWithLabelExpressionParser
      )
    val parsersSeq = defaultParsers ++ expressionConfig.languages.expressionParsers
    val parsers    = parsersSeq.map(p => p.languageId -> p).toMap
    new ExpressionCompiler(parsers, dictRegistry)
  }

}

class ExpressionCompiler(expressionParsers: Map[String, ExpressionParser], dictRegistry: DictRegistry) {

  // used only for services and fragments - in places where component is an Executor instead of a factory
  // that creates Executor
  def compileExecutorComponentNodeParameters(
      parameterDefinitions: List[Parameter],
      nodeParameters: List[NodeParameter],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[CompiledParameter]] = {
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
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[(TypedParameter, Parameter)]] = {

    val redundantMissingValidation = Validations.validateRedundantAndMissingParameters(
      parameterDefinitions,
      nodeParameters ++ nodeBranchParameters.flatMap(_.parameters)
    )
    val paramValidatorsMap = parameterValidatorsMap(parameterDefinitions)
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

    allCompiledParams
      .andThen(allParams => Validations.validateWithCustomValidators(allParams, paramValidatorsMap))
      .combine(redundantMissingValidation.map(_ => List()))
  }

  private def parameterValidatorsMap(parameterDefinitions: List[Parameter])(implicit nodeId: NodeId) =
    parameterDefinitions
      .map(p => p.name -> p.validators.map { v => compileValidator(v, p.name, p.typ) }.sequence)
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
        val paramName = ParameterNaming.getNameForBranchParameter(definition, branchId)

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

  private def substituteDictKeyExpression(expression: Expression, editor: Option[ParameterEditor], paramName: String)(
      implicit nodeId: NodeId
  ) =
    editor match {
      case Some(DictParameterEditor(dictId)) =>
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
                    case None => invalidNel(DictLabelByKeyResolutionFailed(dictId, expr.key, nodeId.id, paramName))
                  }
            }
          )
      case _ => Valid(expression)
    }

  def compileValidator(
      validator: Validator,
      paramName: String,
      paramType: TypingResult
  )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Validator] =
    validator match {
      case v: ValidationExpressionParameterValidatorToCompile =>
        compileValidationExpressionParameterValidator(
          v,
          paramName,
          paramType
        )
      case v => Valid(v)
    }

  private def compileValidationExpressionParameterValidator(
      toCompileValidator: ValidationExpressionParameterValidatorToCompile,
      paramName: String,
      paramType: TypingResult
  )(
      implicit nodeId: NodeId
  ): Validated[NonEmptyList[PartSubGraphCompilationError], ValidationExpressionParameterValidator] =
    compile(
      toCompileValidator.validationExpression,
      fieldName = Some(paramName),
      validationCtx = ValidationContext(
        // TODO in the future, we'd like to support more references, see ValidationExpressionParameterValidator
        Map(ValidationExpressionParameterValidator.variableName -> paramType)
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
              toCompileValidator.validationFailedMessage
            )
          )
      }
    }

  def compile(
      n: Expression,
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

  def compileWithoutContextValidation(n: Expression, fieldName: String, expectedType: TypingResult)(
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
            ProcessCompilationError.ExpressionParserCompilationError(err.message, Some(fieldName), n.expression)
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
      dictRegistry
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
