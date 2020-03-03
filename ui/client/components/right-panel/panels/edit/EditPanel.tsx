import React, {memo} from "react"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import Undo from "./buttons/UndoButton"
import Redo from "./buttons/RedoButton"
import Layout from "./buttons/LayoutButton"
import Copy from "./buttons/CopyButton"
import Delete from "./buttons/DeleteButton"
import Paste from "./buttons/PasteButton"
import {useTranslation} from "react-i18next"
import {PassedProps} from "../../ToolsLayer"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../selectors/other"
import {ToolbarButtons} from "../../../Process/ToolbarButtons"

type Props = Pick<PassedProps,
  | "graphLayoutFunction"
  | "selectionActions">

function EditPanel(props: Props) {
  const capabilities = useSelector(getCapabilities)
  const {graphLayoutFunction, selectionActions} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="EDIT-PANEL" title={t("panels.edit.title", "Edit")}>
      <ToolbarButtons small>
        {capabilities.write ? <Undo/> : null}
        {capabilities.write ? <Redo/> : null}
        {capabilities.write ? <Layout graphLayoutFunction={graphLayoutFunction}/> : null}
        {capabilities.write ? <Copy selectionActions={selectionActions}/> : null}
        {capabilities.write ? <Paste selectionActions={selectionActions}/> : null}
        {capabilities.write ? <Delete selectionActions={selectionActions}/> : null}
        {/*{writeAllowed ? <Cut selectionActions={selectionActions}/> : null}*/}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(EditPanel)
