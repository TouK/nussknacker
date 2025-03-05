import scalafix.v1._
import utils.SchemaInSqlDetector._

import java.nio.file.{Path, Paths}
import scala.meta._
import scala.meta.inputs.Input

class NoSlickTableWithoutSchema extends SemanticRule("NoSlickTableWithoutSchema") {

  private val slickTableSymbol = "slick/relational/RelationalProfile#API#Table#"

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.input match {
      case Input.File(path, _) if isExcluded(path.toNIO)             => Patch.empty
      case Input.VirtualFile(path, _) if isExcluded(Paths.get(path)) => Patch.empty
      case _                                                         => process(doc)
    }
  }

  private def isExcluded(path: Path) = {
    path.getFileName.toString == "DbRef.scala"
  }

  private def process(implicit doc: SemanticDocument) = {
    doc.tree.collect {
      case t @ Type.Apply.After_4_6_0(_, _) if t.symbol.value == slickTableSymbol =>
        Patch.lint(
          Diagnostic(
            id = this.getClass.getSimpleName,
            message = "Use TableWithSchema (from apiWithEnforcedSchema) instead of raw Table",
            position = t.pos
          )
        )
      case t @ Term.Interpolate((Term.Name("sqlu") | Term.Name("sql"), List(arg: Lit.String), _))
          if isMissingRequiredSchema(arg.value) =>
        Patch.lint(
          Diagnostic(
            id = "NoRawSlickSqlu",
            message =
              "Avoid using `sqlu`|`sql` without specifying an explicit schema. Example: `my_schema.users` instead of `users`",
            position = t.pos
          )
        )
    }.asPatch
  }

}
