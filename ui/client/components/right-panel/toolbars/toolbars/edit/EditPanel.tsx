import React, {memo, SyntheticEvent} from "react"
import {CollapsibleToolbar} from "../../CollapsibleToolbar"
import Undo from "./buttons/UndoButton"
import Redo from "./buttons/RedoButton"
import Layout from "./buttons/LayoutButton"
import Copy from "./buttons/CopyButton"
import Delete from "./buttons/DeleteButton"
import Paste from "./buttons/PasteButton"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../../../../reducers/selectors/other"
import {ToolbarButtons} from "../../ToolbarButtons"

type Props = {
  selectionActions: SelectionActions,
}

export type SelectionActions = {
  copy: (event: SyntheticEvent) => void,
  canCopy: boolean,
  cut: (event: SyntheticEvent) => void,
  canCut: boolean,
  paste: (event: SyntheticEvent) => void,
  canPaste: boolean,
}

function EditPanel(props: Props) {
  const capabilities = useSelector(getCapabilities)
  const {selectionActions} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="EDIT-PANEL" title={t("panels.edit.title", "Edit")}>
      <ToolbarButtons small>
        {capabilities.write ? <Undo/> : null}
        {capabilities.write ? <Redo/> : null}
        {capabilities.write ? <Layout/> : null}
        {capabilities.write ? <Copy selectionActions={selectionActions}/> : null}
        {capabilities.write ? <Paste selectionActions={selectionActions}/> : null}
        {capabilities.write ? <Delete selectionActions={selectionActions}/> : null}
        {/*{writeAllowed ? <Cut selectionActions={selectionActions}/> : null}*/}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(EditPanel)
