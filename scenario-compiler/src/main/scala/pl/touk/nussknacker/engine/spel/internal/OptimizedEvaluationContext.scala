package pl.touk.nussknacker.engine.spel.internal

import org.springframework.core.convert.{ConversionService, TypeDescriptor}
import org.springframework.expression.{EvaluationContext, MethodExecutor, MethodResolver, PropertyAccessor}
import org.springframework.expression.spel.support._
import pl.touk.nussknacker.engine.api.{Context, SpelExpressionExcludeList}
import pl.touk.nussknacker.engine.api.spel.SpelConversionsProvider
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.globalvariables.ExpressionConfigDefinition
import pl.touk.nussknacker.engine.extension.ExtensionMethodResolver
import pl.touk.nussknacker.engine.spel.{internal, NuReflectiveMethodExecutor}
import pl.touk.nussknacker.engine.spel.internal.propertyAccessors.MethodAccessChecker

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

class EvaluationContextPreparer(
    classLoader: ClassLoader,
    expressionImports: List[String],
    propertyAccessors: Seq[PropertyAccessor],
    conversionService: ConversionService,
    spelExpressionExcludeList: SpelExpressionExcludeList,
    classDefinitionSet: ClassDefinitionSet
) {

  // this method is evaluated for *each* expression evaluation, we want to extract as much as possible to fields in this class
  def prepareEvaluationContext(ctx: Context, globals: Map[String, Any]): EvaluationContext = {
    val optimized = new OptimizedEvaluationContext(ctx, globals)
    optimized.setTypeLocator(locator)
    optimized.setPropertyAccessors(propertyAccessorsList)
    optimized.setTypeConverter(new StandardTypeConverter(conversionService))
    optimized.setMethodResolvers(optimizedMethodResolvers)
    optimized
  }

  private val propertyAccessorsList = propertyAccessors.asJava

  private val locator: StandardTypeLocator = new StandardTypeLocator(classLoader) {
    expressionImports.foreach(registerImport)
  }

  private val optimizedMethodResolvers: java.util.List[MethodResolver] = {
    val mr = new ReflectiveMethodResolver {
      private val extensionMethodResolver = new ExtensionMethodResolver(classDefinitionSet)

      override def resolve(
          context: EvaluationContext,
          targetObject: Object,
          name: String,
          argumentTypes: util.List[TypeDescriptor]
      ): MethodExecutor = {
        val methodExecutor =
          super.resolve(context, targetObject, name, argumentTypes).asInstanceOf[ReflectiveMethodExecutor]
        if (methodExecutor != null) {
          spelExpressionExcludeList.blockExcluded(targetObject, name)
          new NuReflectiveMethodExecutor(methodExecutor)
        } else {
          // If methods are found by default resolver, we fallback to the extension methods resolver
          extensionMethodResolver.resolve(context, targetObject, name, argumentTypes)
        }
      }

    }
    Collections.singletonList(mr)
  }

}

class OptimizedEvaluationContext(ctx: Context, globals: Map[String, Any]) extends StandardEvaluationContext {

  // We *don't* want to initialize any Maps here, as this code is in our tightest loop
  override def lookupVariable(name: String): AnyRef = {
    ctx
      .get(name)
      .orElse(globals.get(name))
      .orNull
      .asInstanceOf[AnyRef]
  }

  override def setVariable(name: String, value: AnyRef): Unit = throw new IllegalArgumentException(
    s"Cannot set variable: $name"
  )

}

object EvaluationContextPreparer {

  def default(
      classLoader: ClassLoader,
      expressionConfig: ExpressionConfigDefinition,
      classDefinitionSet: ClassDefinitionSet
  ): EvaluationContextPreparer = {
    val conversionService = determineConversionService(expressionConfig)
    val propertyAccessors =
      internal.propertyAccessors.configured(
        MethodAccessChecker.create(classDefinitionSet, expressionConfig.dynamicPropertyAccessAllowed),
        classDefinitionSet,
      )
    new EvaluationContextPreparer(
      classLoader,
      expressionConfig.globalImports,
      propertyAccessors,
      conversionService,
      expressionConfig.spelExpressionExcludeList,
      classDefinitionSet
    )
  }

  private def determineConversionService(expressionConfig: ExpressionConfigDefinition) = {
    val spelConversionServices = expressionConfig.customConversionsProviders.collect {
      case spelProvider: SpelConversionsProvider => spelProvider.getConversionService
    }
    spelConversionServices match {
      case Nil         => DefaultSpelConversionsProvider.getConversionService
      case head :: Nil => head
      case moreThanOne =>
        throw new IllegalArgumentException(
          s"More than one SpelConversionsProvider configured: ${moreThanOne.mkString(", ")}"
        )
    }
  }

}
