package pl.touk.nussknacker.engine.spel

import java.lang.reflect.{Method, Modifier}
import java.time.{LocalDate, LocalDateTime}
import java.util
import java.util.Collections
import java.util.concurrent.TimeoutException

import cats.data.{NonEmptyList, State, StateT, Validated}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.springframework.core.convert.TypeDescriptor
import org.springframework.expression._
import org.springframework.expression.common.CompositeStringExpression
import org.springframework.expression.spel.ast.SpelNodeImpl
import org.springframework.expression.spel.support.{ReflectiveMethodExecutor, ReflectiveMethodResolver, StandardEvaluationContext, StandardTypeLocator}
import org.springframework.expression.spel.{SpelCompilerMode, SpelParserConfiguration, standard}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.lazyy.{ContextWithLazyValuesProvider, LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, TypedMapDefinition}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.compiledgraph.expression.{ExpressionParseError, ExpressionParser, ValueWithLazyContext}
import pl.touk.nussknacker.engine.functionUtils.CollectionUtils

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class SpelExpression(parsed: org.springframework.expression.Expression,
                     val original: String,
                     expectedReturnType: ClazzRef,
                     expressionFunctions: Map[String, Method],
                     expressionImports: List[String],
                     propertyAccessors: Seq[PropertyAccessor],
                     classLoader: ClassLoader) extends compiledgraph.expression.Expression with LazyLogging {

  import pl.touk.nussknacker.engine.spel.SpelExpressionParser._

  private val expectedClass = expectedReturnType.clazz

  override def evaluate[T](ctx: Context,
                           lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]] = logOnException(ctx) {
    val simpleContext = new StandardEvaluationContext()
    val locator = new StandardTypeLocator(classLoader)
    expressionImports.foreach(locator.registerImport)
    simpleContext.setTypeLocator(locator)
    propertyAccessors.foreach(simpleContext.addPropertyAccessor)

    simpleContext.setMethodResolvers(optimizedMethodResolvers())

    ctx.variables.foreach {
      case (k, v) => simpleContext.setVariable(k, v)
    }
    simpleContext.setVariable(LazyValuesProviderVariableName, lazyValuesProvider)
    simpleContext.setVariable(LazyContextVariableName, ctx.lazyContext)
    expressionFunctions.foreach {
      case (k, v) => simpleContext.registerFunction(k, v)
    }
    //TODO: async evaluation of lazy vals...
    val value = parsed.getValue(simpleContext, expectedClass).asInstanceOf[T]
    val modifiedLazyContext = simpleContext.lookupVariable(LazyContextVariableName).asInstanceOf[LazyContext]
    Future.successful(ValueWithLazyContext(value, modifiedLazyContext))
  }

  private def optimizedMethodResolvers() : java.util.List[MethodResolver] = {
    val mr = new ReflectiveMethodResolver {
      override def resolve(context: EvaluationContext, targetObject: scala.Any, name: String, argumentTypes: util.List[TypeDescriptor]): MethodExecutor = {
        val methodExecutor = super.resolve(context, targetObject, name, argumentTypes).asInstanceOf[ReflectiveMethodExecutor]
        new OmitAnnotationsMethodExecutor(methodExecutor)
      }
    }
    Collections.singletonList(mr)
  }

  private def logOnException[A](ctx: Context)(block: => A): A = {
    try {
      block
    } catch {
      case NonFatal(e) =>
        logger.info(s"Expression evaluation failed. Original {}, ctxId: {}, message: {}", original, ctx.id, e.getMessage)
        //we log twice here because LazyLogging cannot print context and stacktrace at the same time
        logger.debug("Expression evaluation failed. Original: {}. Context: {}", original, ctx)
        logger.debug("Expression evaluation failed", e)
        throw e
    }
  }
}

class SpelExpressionParser(expressionFunctions: Map[String, Method], expressionImports: List[String],
                           classLoader: ClassLoader, lazyValuesTimeout: Duration, enableSpelForceCompile: Boolean) extends ExpressionParser {

  import pl.touk.nussknacker.engine.spel.SpelExpressionParser._

  override final val languageId: String = SpelExpressionParser.languageId

  private val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
    //we have to pass classloader, because default contextClassLoader can be sth different than we expect...
    new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, classLoader)
  )

  private val scalaLazyPropertyAccessor = new ScalaLazyPropertyAccessor(lazyValuesTimeout)
  private val scalaPropertyAccessor = new ScalaPropertyAccessor
  private val scalaOptionOrNullPropertyAccessor = new ScalaOptionOrNullPropertyAccessor
  private val staticPropertyAccessor = new StaticPropertyAccessor

  private val propertyAccessors = Seq(
    scalaLazyPropertyAccessor, // must be before scalaPropertyAccessor
    scalaOptionOrNullPropertyAccessor, // // must be before scalaPropertyAccessor
    scalaPropertyAccessor,
    staticPropertyAccessor,
    MapPropertyAccessor,
    TypedMapPropertyAccessor
  )

  private val validator = new SpelExpressionValidator()(classLoader)

  override def parseWithoutContextValidation(original: String, expectedType: ClazzRef): Validated[ExpressionParseError, compiledgraph.expression.Expression] = {
    Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => ExpressionParseError(ex.getMessage)).map { parsed =>
      expression(parsed, original, expectedType)

    }
  } 

  override def parse(original: String, ctx: ValidationContext, expectedType: ClazzRef): Validated[NonEmptyList[ExpressionParseError], (TypingResult, compiledgraph.expression.Expression)] = {
    Validated.catchNonFatal(parser.parseExpression(original)).leftMap(ex => NonEmptyList.of(ExpressionParseError(ex.getMessage))).andThen { parsed =>
      validator.validate(parsed, ctx, expectedType).map((_, parsed))
    }.map { case (typingResult, parsed) =>
      (typingResult, expression(parsed, original, expectedType))
    }
  }

  private def expression(expression: Expression, original: String, expectedType: ClazzRef) = {
    if (enableSpelForceCompile) {
      forceCompile(expression)
    }
    new SpelExpression(expression, original, expectedType, expressionFunctions, expressionImports, propertyAccessors, classLoader)
  }

}

object SpelExpressionParser extends LazyLogging {

  val languageId: String = "spel"

  private[spel] final val LazyValuesProviderVariableName: String = "$lazy"
  private[spel] final val LazyContextVariableName: String = "$lazyContext"

  //TODO
  //this does not work in every situation - e.g expression (#variable != '') is not compiled
  //maybe we could remove it altogether with "enableSpelForceCompile" flag after some investigation
  private[spel] def forceCompile(parsed: Expression): Unit = {
    parsed match {
      case e:standard.SpelExpression => forceCompile(e)
      case e:CompositeStringExpression => e.getExpressions.foreach(forceCompile)
    }
  }

  private def forceCompile(spel: standard.SpelExpression): Unit = {
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


  //caching?
  def default(loader: ClassLoader, enableSpelForceCompile: Boolean, imports: List[String]): SpelExpressionParser = new SpelExpressionParser(Map(
    "today" -> classOf[LocalDate].getDeclaredMethod("now"),
    "now" -> classOf[LocalDateTime].getDeclaredMethod("now"),
    "distinct" -> classOf[CollectionUtils].getDeclaredMethod("distinct", classOf[java.util.Collection[_]]),
    "sum" -> classOf[CollectionUtils].getDeclaredMethod("sum", classOf[java.util.Collection[_]])
  ), //FIXME: configurable timeout...
    imports, loader, 1 minute, enableSpelForceCompile)


  class ScalaPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name)


    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext) = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses = null
  }

  class StaticPropertyAccessor extends PropertyAccessor with ReadOnly with StaticMethodCaching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.asInstanceOf[Class[_]].getMethods.find(m =>
        m.getParameterCount == 0 && m.getName == name && Modifier.isStatic(m.getModifiers)
      )
    }

    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  class ScalaOptionOrNullPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] = {
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name && classOf[Option[_]].isAssignableFrom(m.getReturnType))
    }

    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext) = {
      method.invoke(target).asInstanceOf[Option[Any]].orNull
    }

    override def getSpecificTargetClasses = null
  }


  class ScalaLazyPropertyAccessor(lazyValuesTimeout: Duration) extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.find(
        m => m.getParameterCount == 0 &&
        m.getReturnType == classOf[State[_,_]] &&
        m.getName == name)

    override protected def invokeMethod(method: Method, target: Any, context: EvaluationContext)  = {
      val f = method
        .invoke(target)
        .asInstanceOf[StateT[IO, ContextWithLazyValuesProvider, Any]]
      val lazyProvider = context.lookupVariable(LazyValuesProviderVariableName).asInstanceOf[LazyValuesProvider]
      val ctx = context.lookupVariable(LazyContextVariableName).asInstanceOf[LazyContext]
      val futureResult = f.run(ContextWithLazyValuesProvider(ctx, lazyProvider))
      //TODO: async invocation :)
      val (modifiedContext, value) = futureResult.unsafeRunTimed(lazyValuesTimeout)
        .getOrElse(throw new TimeoutException(s"Timout on evaluation ${method.getDeclaringClass}:${method.getName}"))
      context.setVariable(LazyContextVariableName, modifiedContext.context)
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

  object TypedMapPropertyAccessor extends PropertyAccessor with ReadOnly {
    //in theory this always happends, because we typed it properly ;)
    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      target.asInstanceOf[TypedMap].fields.contains(name)

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      new TypedValue(target.asInstanceOf[TypedMap].fields(name))

    override def getSpecificTargetClasses = Array(classOf[TypedMap])
  }

  trait Caching extends CachingBase { self: PropertyAccessor =>

    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      !target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override protected def extractClassFromTarget(target: Any): Class[_] =
      Option(target).map(_.getClass).orNull
  }

  trait StaticMethodCaching extends CachingBase { self: PropertyAccessor =>
    override def canRead(context: EvaluationContext, target: scala.Any, name: String) =
      target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override protected def extractClassFromTarget(target: Any): Class[_] = target.asInstanceOf[Class[_]]
  }

  trait CachingBase { self: PropertyAccessor =>
    private val methodsCache = new TrieMap[(String, Class[_]), Option[Method]]()

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      findMethod(name, target)
        .map { method =>
          new TypedValue(invokeMethod(method, target, context))
        }
        .getOrElse(throw new IllegalAccessException("Property is not readable"))

    protected def findMethod(name: String, target: Any): Option[Method] = {
      val targetClass = extractClassFromTarget(target)
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }

    protected def extractClassFromTarget(target: Any): Class[_]
    protected def invokeMethod(method: Method, target: Any, context: EvaluationContext): Any
    protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method]
  }

  trait ReadOnly { self: PropertyAccessor =>

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

  }

}

