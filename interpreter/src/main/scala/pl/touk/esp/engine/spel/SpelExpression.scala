package pl.touk.esp.engine.spel

import java.lang.reflect.Method
import java.time.{LocalDate, LocalDateTime}

import cats.data.Validated
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import org.springframework.expression.{EvaluationContext, PropertyAccessor, TypedValue}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.lazyy.{ContextWithLazyValuesProvider, LazyValuesProvider}
import pl.touk.esp.engine.api.{Context, ValueWithModifiedContext}
import pl.touk.esp.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser}
import pl.touk.esp.engine.functionUtils.CollectionUtils
import pl.touk.esp.engine.spel.SpelExpressionParser.{MapPropertyAccessor, ScalaLazyPropertyAccessor, ScalaPropertyAccessor, _}

import scala.collection.concurrent.TrieMap

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String,
                     expressionFunctions: Map[String, Method],
                     propertyAccessors: Seq[PropertyAccessor]) extends compiledgraph.expression.Expression {

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): ValueWithModifiedContext[T] = {
    val simpleContext = new StandardEvaluationContext()
    propertyAccessors.foreach(simpleContext.addPropertyAccessor)

    ctx.variables.foreach {
      case (k, v) => simpleContext.setVariable(k, v)
    }
    simpleContext.setVariable(LazyValuesProviderVariableName, lazyValuesProvider)
    simpleContext.setVariable(ModifiedContextVariableName, ctx)
    expressionFunctions.foreach {
      case (k, v) => simpleContext.registerFunction(k, v)
    }
    val value = parsed.getValue(simpleContext).asInstanceOf[T]
    val modifiedContext = simpleContext.lookupVariable(ModifiedContextVariableName).asInstanceOf[Context]
    ValueWithModifiedContext(value, modifiedContext)
  }
}

class SpelExpressionParser(expressionFunctions: Map[String, Method]) extends ExpressionParser {

  override final val languageId: String = SpelExpressionParser.languageId

  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
    new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, null)
  )

  private val scalaLazyPropertyAccessor = new ScalaLazyPropertyAccessor
  private val scalaPropertyAccessor = new ScalaPropertyAccessor

  private val propertyAccessors = Seq(
    scalaLazyPropertyAccessor, // must be before scalaPropertyAccessor
    scalaPropertyAccessor,
    MapPropertyAccessor
  )

  override def parse(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    for {
      parsed <- Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage))
    } yield new SpelExpression(parsed, original, expressionFunctions, propertyAccessors)
  }

}

object SpelExpressionParser {

  val languageId: String = "spel"

  private[spel] final val LazyValuesProviderVariableName: String = "$lazy"
  private[spel] final val ModifiedContextVariableName: String = "$modifiedContext"

  val default: SpelExpressionParser = new SpelExpressionParser(Map(
    "today" -> classOf[LocalDate].getDeclaredMethod("now"),
    "now" -> classOf[LocalDateTime].getDeclaredMethod("now"),
    "distinct" -> classOf[CollectionUtils].getDeclaredMethod("distinct", classOf[java.util.Collection[_]]),
    "sum" -> classOf[CollectionUtils].getDeclaredMethod("sum", classOf[java.util.Collection[_]])
  ))


  class ScalaPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name)


    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext) = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses = null
  }

  class ScalaLazyPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.find(
        m => m.getParameterCount == 0 &&
        m.getReturnType == classOf[_ => _] &&
        m.getName == name)

    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext)  = {
      val f = method
        .invoke(target)
        .asInstanceOf[ContextWithLazyValuesProvider => (ContextWithLazyValuesProvider, Any)]
      val lazyProvider = context.lookupVariable(LazyValuesProviderVariableName).asInstanceOf[LazyValuesProvider]
      val ctx = context.lookupVariable(ModifiedContextVariableName).asInstanceOf[Context]
      val (modifiedContext, value) = f(ContextWithLazyValuesProvider(ctx, lazyProvider))
      context.setVariable(ModifiedContextVariableName, modifiedContext.context)
      value
    }

    override def getSpecificTargetClasses = null

  }

  object MapPropertyAccessor extends PropertyAccessor with ReadOnly {

    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      target.asInstanceOf[java.util.Map[_, _]].containsKey(name)

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      new TypedValue(target.asInstanceOf[java.util.Map[_, _]].get(name))

    override def getSpecificTargetClasses = Array(classOf[java.util.Map[_, _]])
  }

  trait Caching { self: PropertyAccessor =>

    private val methodsCache = new TrieMap[(String, Class[_]), Option[Method]]()

    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      !target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      findMethod(name, target)
        .map { method =>
          new TypedValue(invokeMethod(method, target, context))
        }
        .getOrElse(throw new IllegalAccessException("Property is not readable"))

    private def findMethod(name: String, target: Any) = {
      val targetClass = target.getClass
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }

    protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method]

    protected def invokeMethod(method: Method, target: Any, context: EvaluationContext): Any

  }

  trait ReadOnly { self: PropertyAccessor =>

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

  }

}