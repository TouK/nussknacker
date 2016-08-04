package pl.touk.esp.engine.spel

import java.lang.reflect.Method
import java.time.{LocalDate, LocalDateTime}

import cats.data.{Validated, Xor}
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser}
import pl.touk.esp.engine.functionUtils.CollectionUtils

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String,
                     expressionFunctions: Map[String, Method]) extends compiledgraph.expression.Expression {

  override def evaluate[T](ctx: Context): T = {
    val simpleContext = new StandardEvaluationContext()
    ctx.variables.foreach {
      case (k, v) => simpleContext.setVariable(k, v)
    }
    expressionFunctions.foreach {
      case (k, v) => simpleContext.registerFunction(k, v)
    }
    parsed.getValue(simpleContext).asInstanceOf[T]
  }
}

class SpelExpressionParser(expressionFunctions: Map[String, Method]) extends ExpressionParser {

  override final val languageId: String = SpelExpressionParser.languageId

  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
    new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, null)
  )

  override def parse(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    for {
      parsed <- Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage))
    } yield new SpelExpression(parsed, original, expressionFunctions)
  }

}

object SpelExpressionParser {

  val languageId: String = "spel"

  val default: SpelExpressionParser = new SpelExpressionParser(Map(
    "today" -> classOf[LocalDate].getDeclaredMethod("now"),
    "now" -> classOf[LocalDateTime].getDeclaredMethod("now"),
    "distinct" -> classOf[CollectionUtils].getDeclaredMethod("distinct", classOf[java.util.Collection[_]]),
    "sum" -> classOf[CollectionUtils].getDeclaredMethod("sum", classOf[java.util.Collection[_]])
  ))

}