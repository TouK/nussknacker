package pl.touk.nussknacker.engine.spel.internal

import java.lang.reflect.Method
import java.util
import java.util.Collections
import org.springframework.core.convert.TypeDescriptor
import org.springframework.expression.{EvaluationContext, EvaluationException, MethodExecutor, MethodResolver, PropertyAccessor}
import org.springframework.expression.spel.support.{ReflectiveMethodExecutor, ReflectiveMethodResolver, StandardEvaluationContext, StandardTypeLocator}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.spel.{OmitAnnotationsMethodExecutor, SpelExpressionEvaluationException}
import pl.touk.nussknacker.engine.api.process.SpelExpressionBlacklist

import scala.collection.JavaConverters._

class EvaluationContextPreparer(classLoader: ClassLoader,
                                expressionImports: List[String],
                                propertyAccessors: Seq[PropertyAccessor],
                                expressionFunctions: Map[String, Method],
                                spelExpressionBlacklist: SpelExpressionBlacklist) {

  //this method is evaluated for *each* expression evaluation, we want to extract as much as possible to fields in this class
  def prepareEvaluationContext(ctx: Context, globals: Map[String, Any]): EvaluationContext = {
    val optimized = new OptimizedEvaluationContext(ctx, globals, expressionFunctions)
    optimized.setTypeLocator(locator)
    optimized.setPropertyAccessors(propertyAccessorsList)
    optimized.setMethodResolvers(optimizedMethodResolvers)
    optimized
  }

  private val propertyAccessorsList = propertyAccessors.asJava

  private val locator: StandardTypeLocator = new StandardTypeLocator(classLoader) {
    expressionImports.foreach(registerImport)
  }

  private def blockBlacklisted(targetObject: scala.Any, methodName: String): Unit = {
    //todo: get blacklist from ExpressionConfig

    val classFullName = targetObject.asInstanceOf[Class[_]].getName
    spelExpressionBlacklist.blacklistedPatterns.find(blackListRegex =>
      blackListRegex.findFirstMatchIn(classFullName) match {
        case Some(_) => true
        case None => false
      }) match {
      case Some(_) => throw new EvaluationException(s"Method ${methodName} of class ${classFullName} is not allowed to be passed as a spel expression")
      case _ =>
    }
  }

  private val optimizedMethodResolvers: java.util.List[MethodResolver] = {
    val mr = new ReflectiveMethodResolver {
      override def resolve(context: EvaluationContext, targetObject: scala.Any, name: String, argumentTypes: util.List[TypeDescriptor]): MethodExecutor = {
        val methodExecutor = super.resolve(context, targetObject, name, argumentTypes).asInstanceOf[ReflectiveMethodExecutor]

        blockBlacklisted(targetObject, name)

        val methodExecutorNoAnnotations = new OmitAnnotationsMethodExecutor(methodExecutor)

        methodExecutorNoAnnotations
      }

    }

    Collections.singletonList(mr)
  }

}

class OptimizedEvaluationContext(ctx: Context, globals: Map[String, Any],
                                 expressionFunctions: Map[String, Method]) extends StandardEvaluationContext {

  //We *don't* want to initialize any Maps here, as this code is in our tightest loop
  override def lookupVariable(name: String): AnyRef = {
    ctx.get(name)
      .orElse(globals.get(name))
      .orElse(expressionFunctions.get(name))
      .orNull.asInstanceOf[AnyRef]
  }

  override def setVariable(name: String, value: AnyRef): Unit = throw new IllegalArgumentException(s"Cannot set variable: $name")

}
