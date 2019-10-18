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
import org.springframework.expression.common.{CompositeStringExpression, LiteralExpression}
import org.springframework.expression.spel.ast.SpelNodeImpl
import org.springframework.expression.spel.support.{ReflectiveMethodExecutor, ReflectiveMethodResolver, StandardEvaluationContext, StandardTypeLocator}
import org.springframework.expression.spel.{SpelCompilerMode, SpelEvaluationException, SpelParserConfiguration, standard}
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.{ExpressionParseError, ExpressionParser, TypedExpression, ValueWithLazyContext}
import pl.touk.nussknacker.engine.api.lazyy.{ContextWithLazyValuesProvider, LazyContext, LazyValuesProvider}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.functionUtils.CollectionUtils
import pl.touk.nussknacker.engine.spel.SpelExpressionParser.Flavour

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

/**
  * Workaround for Spel compilation problem when expression's underlying class changes.
  * Spel tries to explicitly cast result of compiled expression to a class that has been cached during the compilation.
  *
  * Problematic scenario:
  * In case compilation occurs with type ArrayList and during evaulation a List is provided ClassCastException is thrown.
  * Workaround:
  * In such case we try to parse and compile expression again.
  *
  * Possible problems:
  * - unless Expression is marked @volatile multiple threads might parse it on their own,
  * - performance problem might occur if the ClassCastException is thrown often (e. g. for consecutive calls to getValue)
  */
final case class ParsedSpelExpression(original: String, parser: () => Validated[NonEmptyList[ExpressionParseError], Expression], initial: Expression) extends LazyLogging {
  @volatile var parsed: Expression = initial

  def getValue[T](context: EvaluationContext, desiredResultType: Class[_]): T = {
    def value(): T = parsed.getValue(context, desiredResultType).asInstanceOf[T]

    try {
      value()
    } catch {
      case e: SpelEvaluationException if Option(e.getCause).exists(_.isInstanceOf[ClassCastException]) =>
        logger.warn("Error during expression evaluation '{}': {}. Trying to compile", original, e.getMessage)
        forceParse()
        value()
    }
  }

  def forceParse(): Unit = {
    //we already parsed this expression successfully, so reparsing should NOT fail
    parsed = parser().getOrElse(throw new RuntimeException(s"Failed to reparse $original - this should not happen!"))
  }
}

class SpelExpression(parsed: ParsedSpelExpression,
                     expectedReturnType: TypingResult,
                     flavour: Flavour,
                     prepareEvaluationContext: Context => EvaluationContext) extends api.expression.Expression with LazyLogging {

  import pl.touk.nussknacker.engine.spel.SpelExpressionParser._

  override val original: String = parsed.original

  override val language: String = flavour.languageId

  private val expectedClass =
    expectedReturnType match {
      case r: SingleTypingResult =>
        r.objType.klass
      case _ =>
        // TOOD: what should happen here?
        classOf[Any]
    }

  override def evaluate[T](ctx: Context,
                           lazyValuesProvider: LazyValuesProvider): Future[ValueWithLazyContext[T]] = logOnException(ctx) {
    if (expectedClass == classOf[SpelExpressionRepr]) {
      return Future.successful(ValueWithLazyContext(SpelExpressionRepr(parsed.parsed, ctx, original).asInstanceOf[T], ctx.lazyContext))
    }

    val evaluationContext = prepareEvaluationContext(ctx)
    evaluationContext.setVariable(LazyValuesProviderVariableName, lazyValuesProvider)
    evaluationContext.setVariable(LazyContextVariableName, ctx.lazyContext)

    //TODO: async evaluation of lazy vals...
    val value = parsed.getValue[T](evaluationContext, expectedClass)
    val modifiedLazyContext = evaluationContext.lookupVariable(LazyContextVariableName).asInstanceOf[LazyContext]
    Future.successful(ValueWithLazyContext(value, modifiedLazyContext))
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

class SpelExpressionParser(parser: org.springframework.expression.spel.standard.SpelExpressionParser,
                           validator: SpelExpressionValidator,
                           enableSpelForceCompile: Boolean,
                           flavour: Flavour,
                           prepareEvaluationContext: Context => EvaluationContext) extends ExpressionParser {

  import pl.touk.nussknacker.engine.spel.SpelExpressionParser._

  override final val languageId: String = flavour.languageId

  override def parseWithoutContextValidation(original: String): Validated[NonEmptyList[ExpressionParseError], api.expression.Expression] = {
    baseParse(original).map { parsed =>
      expression(ParsedSpelExpression(original, () => baseParse(original), parsed), Unknown)
    }
  }

  override def parse(original: String, ctx: ValidationContext, expectedType: TypingResult): Validated[NonEmptyList[ExpressionParseError], TypedExpression] = {
    baseParse(original).andThen { parsed =>
      validator.validate(parsed, ctx, expectedType).map((_, parsed))
    }.map { case (typingResult, parsed) =>
      TypedExpression(expression(ParsedSpelExpression(original, () => baseParse(original), parsed), expectedType), typingResult)
    }
  }

  private def baseParse(original: String): Validated[NonEmptyList[ExpressionParseError], Expression] = {
    Validated.catchNonFatal(parser.parseExpression(original, flavour.parserContext.orNull)).leftMap(ex => NonEmptyList.of(ExpressionParseError(ex.getMessage)))
  }

  private def expression(expression: ParsedSpelExpression, expectedType: TypingResult) = {
    if (enableSpelForceCompile) {
      forceCompile(expression.parsed)
    }
    new SpelExpression(expression, expectedType, flavour, prepareEvaluationContext)
  }

}

object SpelExpressionParser extends LazyLogging {

  sealed abstract class Flavour(val languageId: String, val parserContext: Option[ParserContext])
  object Standard extends Flavour("spel", None)
  //TODO: should we enable other prefixes/suffixes?
  object Template extends Flavour("spelTemplate", Some(ParserContext.TEMPLATE_EXPRESSION))

  private[spel] final val LazyValuesProviderVariableName: String = "$lazy"
  private[spel] final val LazyContextVariableName: String = "$lazyContext"

  //TODO
  //this does not work in every situation - e.g expression (#variable != '') is not compiled
  //maybe we could remove it altogether with "enableSpelForceCompile" flag after some investigation
  private[spel] def forceCompile(parsed: Expression): Unit = {
    parsed match {
      case e:standard.SpelExpression => forceCompile(e)
      case e:CompositeStringExpression => e.getExpressions.foreach(forceCompile)
      case e:LiteralExpression =>   
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
  def default(classLoader: ClassLoader, enableSpelForceCompile: Boolean, imports: List[String], flavour: Flavour): SpelExpressionParser = {
    val functions = Map(
      "today" -> classOf[LocalDate].getDeclaredMethod("now"),
      "now" -> classOf[LocalDateTime].getDeclaredMethod("now"),
      "distinct" -> classOf[CollectionUtils].getDeclaredMethod("distinct", classOf[util.Collection[_]]),
      "sum" -> classOf[CollectionUtils].getDeclaredMethod("sum", classOf[util.Collection[_]])
    )
    //FIXME: configurable timeout...
    val lazyValuesTimeout = 1 minute
    val parser = new org.springframework.expression.spel.standard.SpelExpressionParser(
      //we have to pass classloader, because default contextClassLoader can be sth different than we expect...
      new SpelParserConfiguration(SpelCompilerMode.IMMEDIATE, classLoader)
    )
    val propertyAccessors = Seq(
      new ScalaLazyPropertyAccessor(lazyValuesTimeout), // must be before scalaPropertyAccessor
      ScalaOptionOrNullPropertyAccessor, // // must be before scalaPropertyAccessor
      ScalaPropertyAccessor,
      StaticPropertyAccessor,
      MapPropertyAccessor,
      TypedMapPropertyAccessor,
      // it can add performance overhead so it will be better to keep it on the bottom
      MapLikePropertyAccessor
    )

    val validator = new SpelExpressionValidator(new Typer(classLoader))
    new SpelExpressionParser(parser, validator, enableSpelForceCompile, flavour,
      prepareEvaluationContext(classLoader, imports, propertyAccessors, functions))
  }

  private def prepareEvaluationContext[T](classLoader: ClassLoader,
                                          expressionImports: List[String],
                                          propertyAccessors: Seq[PropertyAccessor],
                                          expressionFunctions: Map[String, Method])
                                         (ctx: Context): EvaluationContext = {
    val evaluationContext = new StandardEvaluationContext()
    val locator = new StandardTypeLocator(classLoader)
    expressionImports.foreach(locator.registerImport)
    evaluationContext.setTypeLocator(locator)
    propertyAccessors.foreach(evaluationContext.addPropertyAccessor)

    evaluationContext.setMethodResolvers(optimizedMethodResolvers())

    ctx.variables.foreach {
      case (k, v) => evaluationContext.setVariable(k, v)
    }
    expressionFunctions.foreach {
      case (k, v) => evaluationContext.registerFunction(k, v)
    }
    evaluationContext
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

  object ScalaPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] =
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name)


    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext) = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses = null
  }

  object StaticPropertyAccessor extends PropertyAccessor with ReadOnly with StaticMethodCaching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.asInstanceOf[Class[_]].getMethods.find(m =>
        m.getParameterCount == 0 && m.getName == name && Modifier.isStatic(m.getModifiers)
      )
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  object ScalaOptionOrNullPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] = {
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name && classOf[Option[_]].isAssignableFrom(m.getReturnType))
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext) = {
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

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext)  = {
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

  // mainly for avro's GenericRecord purpose
  object MapLikePropertyAccessor extends PropertyAccessor with Caching with ReadOnly {

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target, propertyName)
    }

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.getMethods.find(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))
    }

    override def getSpecificTargetClasses = null
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
          new TypedValue(invokeMethod(name, method, target, context))
        }
        .getOrElse(throw new IllegalAccessException("Property is not readable"))

    protected def findMethod(name: String, target: Any): Option[Method] = {
      val targetClass = extractClassFromTarget(target)
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }

    protected def extractClassFromTarget(target: Any): Class[_]
    protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any
    protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method]
  }

  trait ReadOnly { self: PropertyAccessor =>

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

  }

}