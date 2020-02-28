import React, {memo} from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import Undo from "./buttons/UndoButton"
import Redo from "./buttons/RedoButton"
import Layout from "./buttons/LayoutButton"
import Properties from "./buttons/PropertiesButton"
import Copy from "./buttons/CopyButton"
import Cut from "./buttons/CutButton"
import Delete from "./buttons/DeleteButton"
import Paste from "./buttons/PasteButton"
import {isSubprocess} from "../../selectors/graph"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../UserRightPanel"

type OwnPropsPick = Pick<PassedProps,
  | "capabilities"
  | "graphLayoutFunction"
  | "selectionActions">

type OwnProps = OwnPropsPick
type Props = OwnProps & StateProps

function EditPanel(props: Props) {
  const {capabilities, graphLayoutFunction, selectionActions, isSubprocess} = props
  const {t} = useTranslation()

  const writeAllowed = capabilities.write

  return (
    <CollapsibleToolbar id="EDIT-PANEL" title={t("panels.edit.title", "Edit")}>
      {writeAllowed ? <Undo/> : null}
      {writeAllowed ? <Redo/> : null}
      {writeAllowed ? <Layout graphLayoutFunction={graphLayoutFunction}/> : null}
      {!isSubprocess ? <Properties/> : null}
      {writeAllowed ? <Copy selectionActions={selectionActions}/> : null}
      {writeAllowed ? <Cut selectionActions={selectionActions}/> : null}
      {writeAllowed ? <Delete selectionActions={selectionActions}/> : null}
      {writeAllowed ? <Paste selectionActions={selectionActions}/> : null}
    </CollapsibleToolbar>
  )
}

const mapState = (state: RootState) => ({
  isSubprocess: isSubprocess(state),
})

type StateProps = ReturnType<typeof mapState>

export default connect(mapState)(memo(EditPanel))
