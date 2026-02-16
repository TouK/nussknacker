package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression.ParseException
import org.springframework.expression.spel.{SpelMessage, SpelParseException}
import pl.touk.nussknacker.engine.api.TemplateEvaluationResult
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.typed.ConversionNecessaryForTypeAssignment
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.dict.{KeysDictTyper, LabelsDictTyper}
import pl.touk.nussknacker.engine.expression.{ExpectedType, IndexBasedTextRange}
import pl.touk.nussknacker.engine.expression.parse.{ExpressionParser, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.SpelExpressionUnderlyingParserError
import pl.touk.nussknacker.engine.spel.internal.EvaluationContextPreparer
import pl.touk.nussknacker.engine.spel.parser.{
  ExceptionWithExpressionTextRange,
  ExpressionWithTextRange,
  NuSpelExpressionParser
}

class SpelExpressionParser(
    immediateCompileParser: NuSpelExpressionParser,
    typer: SpelTyper,
    dictRegistry: DictRegistry,
    enableSpelForceCompile: Boolean,
    flavour: SpelFlavour,
    prepareEvaluationContext: EvaluationContextPreparer
) extends ExpressionParser {

  override final val languageId: Language = flavour.languageId

  override def parse(
      original: String,
      ctx: ValidationContext,
      expectedType: ExpectedType
  ): ValidatedNel[ExpressionParseError, TypedExpression] = {
    val optionalNullExpression =
      if (flavour != SpelFlavour.Template) handleBlankExpressionAsNullExpression(original) else None
    optionalNullExpression.getOrElse {
      parseSpelExpressionUsingImmediateCompileConfiguration(original)
        .andThen { parsed =>
          typer
            .typeExpression(parsed, ctx, flavour)
            .map((parsed, _))
            .leftMap(_.map(_.toParseError(original)))
        }
        .andThen { case (parsed, collectedTypingResult) =>
          validateResultTypeIfNeeded(collectedTypingResult, expectedType)
            .map((parsed, _))
        }
        .map { case (parsed, collectedTypingResult) =>
          TypedExpression(
            createExpression(
              original = original,
              initiallyParsedExpression = parsed,
              expectedType = expectedType.typ
            ),
            collectedTypingResult.typingInfo
          )
        }
    }
  }

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
          .collect {
            case ex: SpelParseException =>
              ex.getMessageCode match {
                case SpelMessage.MORE_INPUT =>
                  // This message sounds better than "After parsing a valid expression, there is still more data in the expression: ''{0}''"
                  "Unexpected text"
                case _ => messageWithoutExpressionAndErrorCodeIndicator(ex)
              }
            case ex: ParseException =>
              ex.getSimpleMessage
          }
          .getOrElse(ex.getMessage)
        NonEmptyList.of(
          SpelExpressionUnderlyingParserError(message, textRangeOpt)
        )
      }
  }

  private def validateResultTypeIfNeeded(
      collected: CollectedTypingResult,
      expectedType: ExpectedType,
  ): ValidatedNel[ExpressionParseError, CollectedTypingResult] = {
    if (expectedType.typ == Typed[SpelExpressionRepr] || expectedType.typ == Typed[
        TemplateEvaluationResult
      ]) {
      Valid(collected)
    } else {
      ExpressionParser
        .validateResultTypeMatchExpectedType(collected.finalResult.typingResult, expectedType)
        .map {
          case ConversionNecessaryForTypeAssignment.NoConvertionIsNeeded =>
            collected
          case ConversionNecessaryForTypeAssignment.ConvertionIsNeeded(typeAfterConversion) =>
            collected.withFinalTypingResult(typeAfterConversion)
        }
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
      new CompiledSpelExpression(
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
      typer.withDictTyper(new LabelsDictTyper(dictRegistry)),
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      prepareEvaluationContext
    )

  def withTyper(modify: SpelTyper => SpelTyper): SpelExpressionParser = {
    new SpelExpressionParser(
      immediateCompileParser,
      modify(typer),
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      prepareEvaluationContext
    )
  }

}

object SpelExpressionParser extends LazyLogging {

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      dictRegistry: DictRegistry,
      enableSpelForceCompile: Boolean,
      flavour: SpelFlavour,
      classDefinitionSet: ClassDefinitionSet,
  ): SpelExpressionParser = {

    // we have to pass classloader, because default contextClassLoader can be sth different than we expect...
    val immediateCompileParser    = NuSpelExpressionParser.immediate(classLoader)
    val evaluationContextPreparer = EvaluationContextPreparer.default(classLoader, expressionConfig, classDefinitionSet)
    val typer = SpelTyper.default(
      classLoader,
      expressionConfig,
      new KeysDictTyper(dictRegistry),
      classDefinitionSet,
      absentVariableReferenceAllowed = false
    )
    new SpelExpressionParser(
      immediateCompileParser,
      typer,
      dictRegistry,
      enableSpelForceCompile,
      flavour,
      evaluationContextPreparer
    )
  }

}
