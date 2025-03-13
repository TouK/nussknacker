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

  def apply(mainEditor: SimpleParameterEditor, additionalEditor: ParameterEditor): ParameterEditors =
    new ParameterEditors(mainEditor, Some(additionalEditor))

  def apply(mainEditor: ParameterEditor, additionalEditor: SimpleParameterEditor): ParameterEditors =
    new ParameterEditors(mainEditor, Some(additionalEditor))

  def unsafeFromList(editors: List[ParameterEditor]): ParameterEditors = editors match {
    case main :: Nil                                                           => ParameterEditors(main)
    case (main: SimpleParameterEditor) :: (additional: ParameterEditor) :: Nil => ParameterEditors(main, additional)
    case (main: ParameterEditor) :: (additional: SimpleParameterEditor) :: Nil => ParameterEditors(main, additional)
    case _ =>
      throw new IllegalArgumentException(
        s"Incorrect configuration of editors: $editors. " +
          s"There are allowed maximum 2 editors"
      )
  }

}
