package pl.touk.esp.engine.spel

import java.lang.reflect.Method
import java.time.{LocalDate, LocalDateTime}

import cats.data.Validated
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import org.springframework.expression.{EvaluationContext, PropertyAccessor, TypedValue}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser}
import pl.touk.esp.engine.functionUtils.CollectionUtils
import pl.touk.esp.engine.spel.SpelExpressionParser.{MapPropertyAccessor, ScalaPropertyAccessor}

import scala.collection.concurrent
import scala.collection.concurrent.TrieMap

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String,
                     expressionFunctions: Map[String, Method]) extends compiledgraph.expression.Expression {

  val scalaPropertyAccessor = new ScalaPropertyAccessor

  override def evaluate[T](ctx: Context): T = {
    val simpleContext = new StandardEvaluationContext()
    simpleContext.addPropertyAccessor(scalaPropertyAccessor)
    simpleContext.addPropertyAccessor(new MapPropertyAccessor)

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

  //TODO: jak bardzo to jest niewydajne???
  class ScalaPropertyAccessor extends PropertyAccessor {

    val methodsCache = new TrieMap[(String, Class[_]), Option[Method]]()

    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      !target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      findMethod(name, target)
        .map(_.invoke(target))
        .map(new TypedValue(_))
        .getOrElse(throw new IllegalAccessException("Property is not readable"))

    private def findMethod(name: String, target: Any) = {
      val targetClass = target.getClass
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }

    private def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.toList.find(m => m.getParameterCount == 0 && m.getName == name)

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

    override def getSpecificTargetClasses = null
  }

  class MapPropertyAccessor extends PropertyAccessor {

    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      target.asInstanceOf[java.util.Map[_, _]].containsKey(name)

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      new TypedValue(target.asInstanceOf[java.util.Map[_, _]].get(name))

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

    override def getSpecificTargetClasses = Array(classOf[java.util.Map[_, _]])
  }

}