package pl.touk.nussknacker.engine.assembly

import org.springframework.util.AntPathMatcher

final case class MergeRule(
    pattern: String,
    strategy: MergeStrategy
)

class MergeRuleResolver(mergeRules: List[MergeRule]) {

  private val matcher = {
    val m = new AntPathMatcher("/")
    m.setCaseSensitive(false)
    m
  }

  private val finalRules = mergeRules ++ MergeRuleResolver.fallbackMergeRules

  def strategyFor(entryName: String): MergeStrategy = {
    finalRules.find(rule => matcher.`match`(rule.pattern, entryName)) match {
      case Some(rule) => rule.strategy
      case None if MergeRuleResolver.isLicenseFile(entryName) || MergeRuleResolver.isReadme(entryName) =>
        MergeStrategy.Rename
      case None => MergeStrategy.Deduplicate
    }
  }

}

object MergeRuleResolver {

  private val fallbackMergeRules: List[MergeRule] = List(
    // META-INF/services should be merged line-by-line
    MergeRule("META-INF/services/**", MergeStrategy.FilterDistinctLines),
    // These META-INF entries are either regenerated or not applicable to the uber-jar. Keeping originals can cause conflicts.
    MergeRule("META-INF/MANIFEST.MF", MergeStrategy.Discard),
    MergeRule("META-INF/INDEX.LIST", MergeStrategy.Discard),
    MergeRule("META-INF/DEPENDENCIES", MergeStrategy.Discard),
    // Discard JPMS module descriptors (a proper solution would be to merge them)
    MergeRule("**/module-info.class", MergeStrategy.Discard),
    // Signature files are invalid after merging and can fail verification
    MergeRule("META-INF/*.SF", MergeStrategy.Discard),
    MergeRule("META-INF/*.DSA", MergeStrategy.Discard),
    MergeRule("META-INF/*.RSA", MergeStrategy.Discard)
  )

  // Based on sbt-assembly
  private def isLicenseFile(entryName: String): Boolean = {
    val LicenseFilePattern = """(\._license|license|licence|notice|copying)([.]\w+)?$""".r
    fileName(entryName).toLowerCase match {
      case LicenseFilePattern(_, ext) if ext != ".class" => true
      case _                                             => false
    }
  }

  // Based on sbt-assembly
  private def isReadme(entryName: String): Boolean = {
    val ReadMePattern = """(readme|about)([.]\w+)?$""".r
    fileName(entryName).toLowerCase match {
      case ReadMePattern(_, ext) if ext != ".class" => true
      case _                                        => false
    }
  }

  private def fileName(path: String): String = {
    val lastSlash = path.lastIndexOf('/')
    if (lastSlash >= 0) path.substring(lastSlash + 1) else path
  }

}
