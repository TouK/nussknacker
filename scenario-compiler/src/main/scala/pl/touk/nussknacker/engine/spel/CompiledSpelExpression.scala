package pl.touk.nussknacker.engine.spel

import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression._
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel._
import org.springframework.expression.spel.ast.SpelNodeImpl
import pl.touk.nussknacker.engine.api.{Context, TemplateEvaluationResult, TemplateRenderedPart}
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypingResult}
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.parser.ExpressionWithTextRange

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
class CompiledSpelExpression(
    override val original: String,
    reparseFunction: () => ValidatedNel[SpelExpressionParseError, ExpressionWithTextRange],
    initiallyParsedExpression: ExpressionWithTextRange,
    expectedReturnType: TypingResult,
    flavour: SpelFlavour,
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
      case SpelFlavour.Standard =>
        parsed.getExpression.getValue(context, expectedClass).asInstanceOf[T]
      case SpelFlavour.Template =>
        val parts            = renderTemplateExpressionParts(context)
        val evaluationResult = TemplateEvaluationResult(parts)
        if (expectedReturnType == Typed[TemplateEvaluationResult]) {
          evaluationResult.asInstanceOf[T]
        } else if (expectedReturnType.canBeLooselyAssignedTo(Typed[CharSequence])) {
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
