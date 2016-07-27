package pl.touk.esp.engine.graph

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import pl.touk.esp.engine.Interpreter.ContextImpl

import scala.language.implicitConversions

object expression {

  implicit def spelToExpression(expr: String): Expression = SpelExpression(expr)

  sealed trait Expression {
    def original: String

    def evaluate[T](ctx: ContextImpl): T
  }

  case class SpelExpression(original: String) extends Expression {

    @transient lazy val parsed = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, null))
      .parseExpression(original)

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

}