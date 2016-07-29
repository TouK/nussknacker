package pl.touk.esp.engine.spel

import cats.data.{Validated, Xor}
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.esp.engine.Interpreter.ContextImpl
import pl.touk.esp.engine._
import pl.touk.esp.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser}

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String) extends compiledgraph.expression.Expression {

  override def evaluate[T](ctx: ContextImpl): T = {
    val simpleContext = new StandardEvaluationContext()
    ctx.variables.foreach {
      case (k, v) => simpleContext.setVariable(k, v)
    }
    ctx.expressionFunctions.foreach {
      case (k, v) => simpleContext.registerFunction(k, v)
    }
    parsed.getValue(simpleContext).asInstanceOf[T]
  }
}

object SpelExpressionParser extends ExpressionParser {

  override final val languageId: String = "spel"

  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
    new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, null)
  )

  override def parse(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    for {
      parsed <- Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage))
    } yield new SpelExpression(parsed, original)
  }

}