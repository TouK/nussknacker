import React, {memo, SyntheticEvent} from "react"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import Undo from "./buttons/UndoButton"
import Redo from "./buttons/RedoButton"
import Layout from "./buttons/LayoutButton"
import Copy from "./buttons/CopyButton"
import Delete from "./buttons/DeleteButton"
import Paste from "./buttons/PasteButton"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../../reducers/selectors/other"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

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

export type CustomAction = {
  name: string
}

function EditPanel(props: Props) {
  const {write} = useSelector(getCapabilities)
  const {selectionActions} = props
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="EDIT-PANEL" title={t("panels.edit.title", "Edit")}>
      <ToolbarButtons small>
        {write ? <Undo/> : null}
        {write ? <Redo/> : null}
        {write ? <Copy selectionActions={selectionActions}/> : null}
        {write ? <Paste selectionActions={selectionActions}/> : null}
        {write ? <Delete selectionActions={selectionActions}/> : null}
        {write ? <Layout/> : null}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(EditPanel)
