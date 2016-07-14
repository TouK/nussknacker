package pl.touk.process.model.graph

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import pl.touk.process.model.api.Ctx

import scala.language.implicitConversions

object expression {

  implicit def spelToExpression(expr: String): Expression = SpelExpression(expr)

  sealed trait Expression {
    def evaluate[T](ctx: Ctx): T
  }

  case class SpelExpression(expr: String) extends Expression {

    @transient val parsed = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, null))
      .parseExpression(expr)

    override def evaluate[T](ctx: Ctx): T = {
      val simpleContext = new StandardEvaluationContext()
      ctx.vars.foreach {
        case (k, v) => simpleContext.setVariable(k, v)
      }
      parsed.getValue(simpleContext).asInstanceOf[T]
    }
  }

}
