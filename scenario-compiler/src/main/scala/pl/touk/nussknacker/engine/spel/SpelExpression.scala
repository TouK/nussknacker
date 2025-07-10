package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.expression._
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel._
import org.springframework.expression.spel.ast.SpelNodeImpl
import pl.touk.nussknacker.engine.api.{Context, TemplateEvaluationResult, TemplateRenderedPart}
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, LabelsDictTyper}
import pl.touk.nussknacker.engine.expression.{IndexBasedTextRange, NullExpression}
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.{Expression => GraphExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.SpelExpressionUnderlyingParserError
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.parser.{
  ExceptionWithExpressionTextRange,
  ExpressionWithTextRange,
  NuSpelExpressionParser
}

import java.util.concurrent.atomic.AtomicBoolean
import scala.util.control.NonFatal

/**
 * Passing both initiallyParsedExpression and reparseFunction is a workaround for Spel compilation problem when
 * expression's underlying class changes. Spel tries to explicitly cast result of compiled expression to
 * a class that has been cached during the compilation.
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
class SpelExpression(
    override val original: String,
    reparseFunction: () => ValidatedNel[SpelExpressionParseError, ExpressionWithTextRange],
    initiallyParsedExpression: ExpressionWithTextRange,
    expectedReturnType: TypingResult,
    flavour: Flavour,
    evaluationContextPreparer: EvaluationContextPreparer
) extends CompiledExpression
    with LazyLogging {

  @volatile private var parsed: ExpressionWithTextRange = initiallyParsedExpression
  private val firstInterpretationFinished               = new AtomicBoolean()

  private[spel] def forceCompile(): Unit = forceCompileSpringExpression(parsed.getExpression)

  // TODO
  // this does not work in every situation - e.g expression (#variable != '') is not compiled
  // maybe we could remove it altogether with "enableSpelForceCompile" flag after some investigation
  private def forceCompileSpringExpression(parsed: Expression): Unit = {
    parsed match {
      case e: standard.SpelExpression   => forceCompileStandardExpression(e)
      case e: CompositeStringExpression => e.getExpressions.foreach(forceCompileSpringExpression)
      case _: LiteralExpression         =>
      case _: NullExpression            =>
    }
  }

  private def forceCompileStandardExpression(spel: standard.SpelExpression): Unit = {
    logger.debug(s"Compiling expression ${spel.getExpressionString}...")
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

  override val language: Language = flavour.languageId

  def parsedSpringExpression: Expression = parsed.getExpression

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
      return SpelExpressionRepr(parsed.getExpression, ctx, globals, original).asInstanceOf[T]
    }
    val evaluationContext = evaluationContextPreparer.prepareEvaluationContext(ctx, globals)
    getValue[T](evaluationContext)
  }

  def getValue[T](context: EvaluationContext): T = {
    try {
      withFirstInterpretationSynchronized {
        getValueDependingOnFlavourAndResultType[T](context)
      }
    } catch {
      case e: SpelEvaluationException if Option(e.getCause).exists(_.isInstanceOf[ClassCastException]) =>
        logger.warn(
          s"Error during expression [$original] evaluation: ${e.getMessage}. Trying to parse expression again",
        )
        parseExpressionAgain()
        try {
          withFirstInterpretationSynchronized {
            getValueDependingOnFlavourAndResultType[T](context)
          }
        } catch {
          case e: SpelEvaluationException if Option(e.getCause).exists(_.isInstanceOf[ClassCastException]) =>
            logger.debug(s"Another attempt of expression [$original] evaluation failed. Exception will be rethrown")
            throw e
        }
    }
  }

  // There is a bug in Spring's SpelExpression class: interpretedCount variable is not synchronized with ReflectiveMethodExecutor.didArgumentConversionOccur.
  // The latter mentioned method check argumentConversionOccurred Boolean which could be false not because conversion not occurred but because method.invoke()
  // isn't finished yet. Due to this problem, an expression that shouldn't be compiled might be compiled. It generates IllegalStateException errors in further evaluations of the expression.
  private def withFirstInterpretationSynchronized[R](run: => R): R = {
    if (!firstInterpretationFinished.get()) {
      synchronized {
        val valueToReturn = run
        firstInterpretationFinished.set(true)
        valueToReturn
      }
    } else {
      run
    }
  }

  private def getValueDependingOnFlavourAndResultType[T](context: EvaluationContext): T = {
    flavour match {
      case SpelExpressionParser.Standard =>
        parsed.getExpression.getValue(context, expectedClass).asInstanceOf[T]
      case SpelExpressionParser.Template =>
        val parts            = renderTemplateExpressionParts(context)
        val evaluationResult = TemplateEvaluationResult(parts)
        if (expectedReturnType == Typed[TemplateEvaluationResult]) {
          evaluationResult.asInstanceOf[T]
        } else if (expectedReturnType.canBeStrictlyAssignedTo(Typed[CharSequence])) {
          evaluationResult.renderedTemplate.asInstanceOf[T]
        } else {
          throw new IllegalStateException(s"Expression parsed with unexpected type: $expectedReturnType")
        }
    }
  }

  private def renderTemplateExpressionParts(evaluationContext: EvaluationContext): List[TemplateRenderedPart] = {
    def renderExpression(expression: Expression): List[TemplateRenderedPart] = expression match {
      case literal: LiteralExpression => List(RenderedLiteral(literal.getExpressionString))
      case spelExpr: org.springframework.expression.spel.standard.SpelExpression =>
        List(RenderedSubExpression(spelExpr.getValue(evaluationContext)))
      case composite: CompositeStringExpression => composite.getExpressions.toList.flatMap(renderExpression)
      case other =>
        throw new IllegalArgumentException(
          s"Unsupported expression type: ${other.getClass.getName} for a template expression"
        )
    }
    renderExpression(parsed.getExpression)
  }

  private def parseExpressionAgain(): Unit = {
    synchronized {
      // We already parsed this expression successfully, so reparsing should NOT fail
      parsed = reparseFunction().getOrElse(
        throw new RuntimeException(s"Failed to reparse $original - this should not happen!")
      )
      firstInterpretationFinished.set(false)
    }
  }

  private def logOnException[A](ctx: Context)(block: => A): A = {
    try {
      block
    } catch {
      case NonFatal(e) =>
        if (logger.underlying.isDebugEnabled) {
          // We log twice here because LazyLogging cannot print context and stacktrace at the same time
          logger.debug(s"Expression evaluation failed. Original: $original. Context: $ctx")
          logger.debug("Expression evaluation failed", e)
        } else {
          logger.info(s"Expression evaluation failed. Original $original, ctxId: ${ctx.id}, message: ${e.getMessage}")
        }
        throw new SpelExpressionEvaluationException(original, e)
    }
  }

}

class SpelExpressionEvaluationException(val expression: String, cause: Throwable)
    extends NonTransientException(
      expression,
      s"Expression [$expression] evaluation failed, message: ${cause.getMessage}",
      cause = cause
    )

class SpelExpressionParser(
    immediateCompileParser: NuSpelExpressionParser,
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
      parseSpelExpressionUsingImmediateCompileConfiguration(original).map { parsed =>
        createExpression(original, parsed, expectedType)
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
      parseSpelExpressionUsingImmediateCompileConfiguration(original)
        .andThen { parsed =>
          validator
            .validate(parsed, ctx, expectedType, flavour)
            .map((_, parsed))
            .leftMap(_.map(_.toParseError(original)))
        }
        .map { case (combinedResult, parsed) =>
          TypedExpression(
            createExpression(
              original = original,
              initiallyParsedExpression = parsed,
              expectedType = expectedType
            ),
            combinedResult.typingInfo
          )
        }
    }
  }

  private def shouldUseNullExpression(original: String): Boolean = flavour != Template && StringUtils.isBlank(original)

  private def parseSpelExpressionUsingImmediateCompileConfiguration(
      original: String
  ): ValidatedNel[SpelExpressionParseError, ExpressionWithTextRange] = {
    Validated
      .catchNonFatal(immediateCompileParser.parseExpression(original, flavour.parserContext.orNull))
      .leftMap { ex =>
        val textRangeOpt = Option(ex)
          .collect {
            case ex: ExceptionWithExpressionTextRange =>
              ex.getExpressionTextRange
            case ex: ParseException =>
              IndexBasedTextRange(ex.getPosition, ex.getPosition + 1)
          }
          .map(_.toCoordinatesBasedTextRange(original))
        val message = Option(ex)
          .collect { case ex: SpelParseException =>
            ex.getMessageCode match {
              case SpelMessage.MORE_INPUT =>
                // This message sounds better than "After parsing a valid expression, there is still more data in the expression: ''{0}''"
                "Unexpected text"
              case _ => messageWithoutExpressionAndErrorCodeIndicator(ex)
            }
          }
          .getOrElse(ex.getMessage)
        NonEmptyList.of(
          SpelExpressionUnderlyingParserError(message, textRangeOpt)
        )
      }
  }

  // SpEL adds:
  // - Expression [<expression>]: prefix - see ExpressionException.toDetailedString
  // - EL1001E: prefix - (error code indicator), see SpelMessage.formatMessage
  // We remove both things to make messages more human-readable
  // To avoid first prefix we call getSimpleMessage instead of getMessage.
  private def messageWithoutExpressionAndErrorCodeIndicator(ex: SpelParseException) =
    ex.getSimpleMessage.replaceFirst("^EL\\d{4}E?: ", "")

  private def createExpression(
      original: String,
      initiallyParsedExpression: ExpressionWithTextRange,
      expectedType: TypingResult
  ) = {
    val expr =
      new SpelExpression(
        original = original,
        // We should consider using parser with compilation off for reparse
        reparseFunction = () => parseSpelExpressionUsingImmediateCompileConfiguration(original),
        initiallyParsedExpression = initiallyParsedExpression,
        expectedReturnType = expectedType,
        flavour = flavour,
        evaluationContextPreparer = prepareEvaluationContext
      )
    if (enableSpelForceCompile) {
      expr.forceCompile()
    }
    expr
  }

  def typingDictLabels =
    new SpelExpressionParser(
      immediateCompileParser,
      validator.withTyper(_.withDictTyper(new LabelsDictTyper(dictRegistry))),
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      prepareEvaluationContext
    )

  def withValidator(modify: SpelExpressionValidator => SpelExpressionValidator): SpelExpressionParser = {
    new SpelExpressionParser(
      immediateCompileParser,
      modify(validator),
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      prepareEvaluationContext
    )
  }

}

object SpelExpressionParser extends LazyLogging {

  sealed abstract class Flavour(val languageId: Language, val parserContext: Option[ParserContext])
  object Standard extends Flavour(GraphExpression.Language.Spel, None)
  // TODO: should we enable other prefixes/suffixes?
  object Template extends Flavour(GraphExpression.Language.SpelTemplate, Some(ParserContext.TEMPLATE_EXPRESSION))

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      dictRegistry: DictRegistry,
      enableSpelForceCompile: Boolean,
      flavour: Flavour,
      classDefinitionSet: ClassDefinitionSet,
  ): SpelExpressionParser = {

    // we have to pass classloader, because default contextClassLoader can be sth different than we expect...
    val immediateCompileParser = new NuSpelExpressionParser(
      new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, classLoader)
    )
    val evaluationContextPreparer = EvaluationContextPreparer.default(classLoader, expressionConfig, classDefinitionSet)
    val validator = new SpelExpressionValidator(
      Typer.default(
        classLoader,
        expressionConfig,
        new KeysDictTyper(dictRegistry),
        classDefinitionSet,
        absentVariableReferenceAllowed = false
      )
    )
    new SpelExpressionParser(
      immediateCompileParser,
      validator,
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      evaluationContextPreparer
    )
  }

}
