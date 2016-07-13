package pl.touk.process.model

import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import pl.touk.process.model.evaluator.Ctx

import scala.language.implicitConversions

object model {

  implicit def toRawExpression(fun: Ctx => Any): Expression = new Expression {
    override def evaluate(ctx: Ctx) = fun(ctx)
  }

  implicit def spelToExpression(expr: String): Expression = SpelExpression(expr)

  sealed trait Node {
    def metaData: MetaData
  }


  case class MetaData(id: String, description: String = "")

  case class StartNode(metaData: MetaData, next: Node) extends Node

  case class End(metaData: MetaData) extends Node

  case class Processor(metaData: MetaData, processor: ProcessorRef, next: Node) extends Node

  case class Enricher(metaData: MetaData, processor: ProcessorRef, output: String, next: Node) extends Node

  case class Filter(metaData: MetaData, expression: Expression, next: Node) extends Node

  case class Switch(metaData: MetaData, expression: Expression, exprVal: String, nexts: List[(Expression, Node)]) extends Node

  sealed trait Expression {
    def evaluate(ctx: Ctx): Any
  }

  case class SpelExpression(expr: String) extends Expression {

    @transient val parser = new SpelExpressionParser()

    @transient val parsed = parser.parseExpression(expr)

    override def evaluate(ctx: Ctx) = {
      val simpleContext = new StandardEvaluationContext()
      ctx.data.foreach {
        case (k, v) => simpleContext.setVariable(k, v)
      }
      ctx.globals.foreach {
        case (k, v) => simpleContext.setVariable(k, v)
      }
      parsed.getValue(simpleContext)
    }
  }

  case class Parameter(name: String, expression: Expression)

  case class ProcessorRef(parameters: List[Parameter], id: String)

}
