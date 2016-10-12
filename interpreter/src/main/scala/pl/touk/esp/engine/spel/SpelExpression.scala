package pl.touk.esp.engine.spel

import java.lang.reflect.Method
import java.time.{LocalDate, LocalDateTime}

import cats.data.{State, Validated}
import com.typesafe.scalalogging.LazyLogging
import org.springframework.expression._
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.lazyy.{ContextWithLazyValuesProvider, LazyValuesProvider}
import pl.touk.esp.engine.api.{Context, ValueWithModifiedContext}
import pl.touk.esp.engine.compile.ValidationContext
import pl.touk.esp.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser}
import pl.touk.esp.engine.functionUtils.CollectionUtils
import pl.touk.esp.engine.spel.SpelExpressionParser.{MapPropertyAccessor, ScalaLazyPropertyAccessor, ScalaPropertyAccessor, _}

import scala.collection.concurrent.TrieMap
import scala.util.control.NonFatal

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String,
                     expressionFunctions: Map[String, Method],
                     propertyAccessors: Seq[PropertyAccessor]) extends compiledgraph.expression.Expression with LazyLogging {

  override def evaluate[T](ctx: Context, lazyValuesProvider: LazyValuesProvider): ValueWithModifiedContext[T] = logOnException(ctx) {
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

  private def logOnException[A](ctx: Context)(block: => A): A = {
    try {
      block
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Expression evaluation failed. Original: $original. Context: $ctx", e)
        throw e
    }
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

  //fixme wydzielic metode
  override def parseWithoutContextValidation(original: String): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage)).map { parsed =>
      // wymuszamy kompilację, żeby nie była wykonywana współbieżnie później
      forceCompile(parsed)
      new SpelExpression(parsed, original, expressionFunctions, propertyAccessors)
    }
  }

  override def parse(original: String, ctx: ValidationContext): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage)).andThen { parsed =>
      new SpelExpressionValidator(parsed, ctx).validate()
    }.map { withReferencesResolved =>
      // wymuszamy kompilację, żeby nie była wykonywana współbieżnie później
      forceCompile(withReferencesResolved)
      new SpelExpression(withReferencesResolved, original, expressionFunctions, propertyAccessors)
    }
  }

  private def forceCompile(parsed: Expression): Unit = {
    def recoveredEvaluate() = try {
      parsed.getValue
    } catch {
      case e: EvaluationException =>
    }
    // robimy dwie ewaluacje bo SpelCompilerMode.IMMEDIATE sprawia że dopiero wtedy jest prawdziwa kompilacja
    recoveredEvaluate()
    recoveredEvaluate()
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
        m.getReturnType == classOf[State[_,_]] &&
        m.getName == name)

    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext)  = {
      val f = method
        .invoke(target)
        .asInstanceOf[State[ContextWithLazyValuesProvider, Any]]
      val lazyProvider = context.lookupVariable(LazyValuesProviderVariableName).asInstanceOf[LazyValuesProvider]
      val ctx = context.lookupVariable(ModifiedContextVariableName).asInstanceOf[Context]
      val (modifiedContext, value) = f.run(ContextWithLazyValuesProvider(ctx, lazyProvider)).value
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