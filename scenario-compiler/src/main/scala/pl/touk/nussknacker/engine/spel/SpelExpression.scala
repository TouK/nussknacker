package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.expression._
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast.SpelNodeImpl
import org.springframework.expression.spel.{
  SpelCompilerMode,
  SpelEvaluationException,
  SpelParserConfiguration,
  standard
}
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, TemplateEvaluationResult, TemplateRenderedPart}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, LabelsDictTyper}
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.{Expression => GraphExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.ExpressionCompilationError
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer

import scala.util.control.NonFatal

/**
  * Workaround for Spel compilation problem when expression's underlying class changes.
  * Spel tries to explicitly cast result of compiled expression to a class that has been cached during the compilation.
  *
  * Problematic scenario:
  * In case compilation occurs with type ArrayList and during evaluation a List is provided ClassCastException is thrown.
  * Workaround:
  * In such case we try to parse and compile expression again.
  *
  * Possible problems:
  * - unless Expression is marked @volatile multiple threads might parse it on their own,
  * - performance problem might occur if the ClassCastException is thrown often (e. g. for consecutive calls to getValue)
  */
final case class ParsedSpelExpression(
    original: String,
    parser: () => ValidatedNel[ExpressionParseError, Expression],
    initial: Expression
) extends LazyLogging {
  @volatile var parsed: Expression = initial

  def getValue[T](context: EvaluationContext, desiredResultType: Class[_]): T = {
    def value(): T = synchronized { parsed.getValue(context, desiredResultType).asInstanceOf[T] }

    try {
      value()
    } catch {
      case e: SpelEvaluationException if Option(e.getCause).exists(_.isInstanceOf[ClassCastException]) =>
        logger.warn("Error during expression evaluation '{}': {}. Trying to compile", original, e.getMessage)
        forceParse()
        value()
    }
  }

  def forceParse(): Unit = {
    // we already parsed this expression successfully, so reparsing should NOT fail
    parsed = parser().getOrElse(throw new RuntimeException(s"Failed to reparse $original - this should not happen!"))
  }

}

class SpelExpressionEvaluationException(val expression: String, val ctxId: String, cause: Throwable)
    extends NonTransientException(
      expression,
      s"Expression [$expression] evaluation failed, message: ${cause.getMessage}",
      cause = cause
    )

class SpelExpression(
    parsed: ParsedSpelExpression,
    expectedReturnType: TypingResult,
    flavour: Flavour,
    evaluationContextPreparer: EvaluationContextPreparer
) extends CompiledExpression
    with LazyLogging {

  override val original: String = parsed.original

  override val language: Language = flavour.languageId

  private val expectedClass =
    expectedReturnType match {
      case r: SingleTypingResult =>
        r.runtimeObjType.klass
      case _ =>
        // TODO: what should happen here?
        classOf[Any]
    }

  // TODO: better interoperability with scala type, mainly: scala.math.BigDecimal, scala.math.BigInt and collections
  override def evaluate[T](ctx: Context, globals: Map[String, Any]): T = logOnException(ctx) {
    if (expectedClass == classOf[SpelExpressionRepr]) {
      return SpelExpressionRepr(parsed.parsed, ctx, globals, original).asInstanceOf[T]
    }
    val evaluationContext = evaluationContextPreparer.prepareEvaluationContext(ctx, globals)
    flavour match {
      case SpelExpressionParser.Standard =>
        parsed.getValue[T](evaluationContext, expectedClass)
      case SpelExpressionParser.Template =>
        val parts = renderTemplateExpressionParts(evaluationContext)
        TemplateEvaluationResult(parts).asInstanceOf[T]
    }
  }

  private def renderTemplateExpressionParts(evaluationContext: EvaluationContext): List[TemplateRenderedPart] = {
    def renderExpression(expression: Expression): List[TemplateRenderedPart] = expression match {
      case literal: LiteralExpression => List(RenderedLiteral(literal.getExpressionString))
      case spelExpr: org.springframework.expression.spel.standard.SpelExpression =>
        // TODO: Should we use the same trick with re-parsing after ClassCastException as we use in ParsedSpelExpression?
        List(RenderedSubExpression(spelExpr.getValue[String](evaluationContext, classOf[String])))
      case composite: CompositeStringExpression => composite.getExpressions.toList.flatMap(renderExpression)
      case other =>
        throw new IllegalArgumentException(
          s"Unsupported expression type: ${other.getClass.getName} for a template expression"
        )
    }
    renderExpression(parsed.parsed)
  }

  private def logOnException[A](ctx: Context)(block: => A): A = {
    try {
      block
    } catch {
      case NonFatal(e) =>
        logger.info(
          s"Expression evaluation failed. Original {}, ctxId: {}, message: {}",
          original,
          ctx.id,
          e.getMessage
        )
        // we log twice here because LazyLogging cannot print context and stacktrace at the same time
        logger.debug("Expression evaluation failed. Original: {}. Context: {}", original, ctx)
        logger.debug("Expression evaluation failed", e)
        throw new SpelExpressionEvaluationException(original, ctx.id, e)
    }
  }

}

class SpelExpressionParser(
    parser: org.springframework.expression.spel.standard.SpelExpressionParser,
    validator: SpelExpressionValidator,
    dictRegistry: DictRegistry,
    enableSpelForceCompile: Boolean,
    flavour: Flavour,
    prepareEvaluationContext: EvaluationContextPreparer
) extends ExpressionParser {

  import pl.touk.nussknacker.engine.spel.SpelExpressionParser._

  override final val languageId: Language = flavour.languageId

  override def parseWithoutContextValidation(
      original: String,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, CompiledExpression] = {
    if (shouldUseNullExpression(original)) {
      Valid(NullExpression(original, flavour))
    } else {
      baseParse(original).map { parsed =>
        expression(ParsedSpelExpression(original, () => baseParse(original), parsed), expectedType)
      }
    }
  }

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: TypingResult
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    if (shouldUseNullExpression(original)) {
      Valid(
        TypedExpression(
          NullExpression(original, flavour),
          SpelExpressionTypingInfo(Map.empty, typing.TypedNull)
        )
      )
    } else {
      baseParse(original)
        .andThen { parsed =>
          validator.validate(parsed, ctx, expectedType).map((_, parsed))
        }
        .map { case (combinedResult, parsed) =>
          TypedExpression(
            expression(ParsedSpelExpression(original, () => baseParse(original), parsed), expectedType),
            combinedResult.typingInfo
          )
        }
    }
  }

  private def shouldUseNullExpression(original: String): Boolean = flavour != Template && StringUtils.isBlank(original)

  private def baseParse(original: String): ValidatedNel[ExpressionCompilationError, Expression] = {
    Validated
      .catchNonFatal(parser.parseExpression(original, flavour.parserContext.orNull))
      .leftMap(ex => NonEmptyList.of(ExpressionCompilationError(ex.getMessage)))
  }

  private def expression(expression: ParsedSpelExpression, expectedType: TypingResult) = {
    if (enableSpelForceCompile) {
      forceCompile(expression.parsed)
    }
    new SpelExpression(expression, expectedType, flavour, prepareEvaluationContext)
  }

  def typingDictLabels =
    new SpelExpressionParser(
      parser,
      validator.withTyper(_.withDictTyper(new LabelsDictTyper(dictRegistry))),
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      prepareEvaluationContext
    )

}

object SpelExpressionParser extends LazyLogging {

  sealed abstract class Flavour(val languageId: Language, val parserContext: Option[ParserContext])
  object Standard extends Flavour(GraphExpression.Language.Spel, None)
  // TODO: should we enable other prefixes/suffixes?
  object Template extends Flavour(GraphExpression.Language.SpelTemplate, Some(ParserContext.TEMPLATE_EXPRESSION))

  private[spel] final val LazyValuesProviderVariableName: String = "$lazy"
  private[spel] final val LazyContextVariableName: String        = "$lazyContext"

  // TODO
  // this does not work in every situation - e.g expression (#variable != '') is not compiled
  // maybe we could remove it altogether with "enableSpelForceCompile" flag after some investigation
  private[spel] def forceCompile(parsed: Expression): Unit = {
    parsed match {
      case e: standard.SpelExpression   => forceCompile(e)
      case e: CompositeStringExpression => e.getExpressions.foreach(forceCompile)
      case _: LiteralExpression         =>
      case _: NullExpression            =>
    }
  }

  private def forceCompile(spel: standard.SpelExpression): Unit = {
    val managedToCompile = spel.compileExpression()
    if (!managedToCompile) {
      spel.getAST match {
        case node: SpelNodeImpl if node.isCompilable =>
          throw new IllegalStateException(s"Failed to compile expression: ${spel.getExpressionString}")
        case _ => logger.debug(s"Expression ${spel.getExpressionString} will not be compiled")
      }
    } else {
      logger.debug(s"Compiled ${spel.getExpressionString} with compiler result: $spel")
    }
  }

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      dictRegistry: DictRegistry,
      enableSpelForceCompile: Boolean,
      flavour: Flavour,
      classDefinitionSet: ClassDefinitionSet
  ): SpelExpressionParser = {

    val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
      // we have to pass classloader, because default contextClassLoader can be sth different than we expect...
      new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, classLoader)
    )
    val evaluationContextPreparer = EvaluationContextPreparer.default(classLoader, expressionConfig, classDefinitionSet)
    val validator = new SpelExpressionValidator(
      Typer.default(classLoader, expressionConfig, new KeysDictTyper(dictRegistry), classDefinitionSet)
    )
    new SpelExpressionParser(
      parser,
      validator,
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      evaluationContextPreparer
    )
  }

}
