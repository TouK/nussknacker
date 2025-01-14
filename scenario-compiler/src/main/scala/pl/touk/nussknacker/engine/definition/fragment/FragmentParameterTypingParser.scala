package pl.touk.nussknacker.engine.definition.fragment

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinition

import scala.util.Try

class FragmentParameterTypingParser(classLoader: ClassLoader, classDefinitions: Set[ClassDefinition]) {

  private val typingResultCache: Cache[String, Try[TypingResult]] =
    Caffeine
      .newBuilder()
      .maximumSize(2500) // In real-world, large system there are ~150 entries, 2500 should always suffice
      .build[String, Try[TypingResult]]()

  def parseClassNameToTypingResult(className: String): Try[TypingResult] = {
    Option(typingResultCache.getIfPresent(className)) match {
      case Some(found) =>
        found
      case None =>
        val result = doParseClassNameToTypingResult(className)
        typingResultCache.put(className, result)
        result
    }
  }

  private def doParseClassNameToTypingResult(className: String): Try[TypingResult] = {
    /*
   TODO: Write this parser in a way that handles arbitrary depth expressions
         One should not use regexes for doing so and rather build AST
     */
    def resolveInnerClass(simpleClassName: String): TypingResult =
      classDefinitions
        .find(classDefinition => classDefinition.clazzName.display == simpleClassName)
        .fold(
          // This is fallback - it may be removed and `ClassNotFound` exception may be thrown here after cleaning up the mess with `FragmentClazzRef` class
          Typed(ClassUtils.getClass(classLoader, simpleClassName))
        ) { classDefinition =>
          classDefinition.clazzName
        }

    val mapPattern  = "Map\\[(.+),\\s*(.+)\\]".r
    val listPattern = "List\\[(.+)\\]".r
    val setPattern  = "Set\\[(.+)\\]".r

    Try(className match {
      case mapPattern(x, y) =>
        val resolvedFirstTypeParam  = resolveInnerClass(x)
        val resolvedSecondTypeParam = resolveInnerClass(y)
        Typed.genericTypeClass[java.util.Map[_, _]](List(resolvedFirstTypeParam, resolvedSecondTypeParam))
      case listPattern(x) =>
        val resolvedTypeParam = resolveInnerClass(x)
        Typed.genericTypeClass[java.util.List[_]](List(resolvedTypeParam))
      case setPattern(x) =>
        val resolvedTypeParam = resolveInnerClass(x)
        Typed.genericTypeClass[java.util.Set[_]](List(resolvedTypeParam))
      case simpleClassName => resolveInnerClass(simpleClassName)
    })
  }

}
