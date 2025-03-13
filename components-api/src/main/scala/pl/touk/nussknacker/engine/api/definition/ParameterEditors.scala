package pl.touk.nussknacker.engine.api.definition

case class ParameterEditors private (mainEditor: ParameterEditor, additionalEditor: Option[ParameterEditor]) {

  lazy val value: List[ParameterEditor] = List(
    Some(mainEditor),
    additionalEditor
  ).flatten

}

object ParameterEditors {
  def apply(editor: ParameterEditor): ParameterEditors =
    new ParameterEditors(editor, None)

  def apply(mainEditor: SimpleParameterEditor, additionalEditor: SpelParameterEditor.type): ParameterEditors =
    new ParameterEditors(mainEditor, Some(additionalEditor))

  def apply(mainEditor: SpelParameterEditor.type, additionalEditor: SimpleParameterEditor): ParameterEditors =
    new ParameterEditors(mainEditor, Some(additionalEditor))

  def unsafeFromList(editors: List[ParameterEditor]): ParameterEditors = editors match {
    case main :: Nil =>
      ParameterEditors(main)
    case (main: SimpleParameterEditor) :: (additional: SpelParameterEditor.type) :: Nil =>
      ParameterEditors(main, additional)
    case (main: SpelParameterEditor.type) :: (additional: SimpleParameterEditor) :: Nil =>
      ParameterEditors(main, additional)
    case _ =>
      throw new IllegalArgumentException(
        s"Incorrect configuration of editors: $editors. A maximum of 2 editors is allowed and if there are 2 editors," +
          s" at least one must be SpelParameterEditor"
      )
  }

}
