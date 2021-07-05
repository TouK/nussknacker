/* eslint-disable i18next/no-literal-string */
import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {undo} from "../../../../actions/undoRedoActions"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/undo.svg"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function UndoButton(props: ToolbarButtonProps): JSX.Element {
  const {undo} = useSelectionActions()
  const history = useSelector(getHistory)
  const {t} = useTranslation()
  const {disabled} = props
  const available = !disabled && history.past.length > 0 && undo

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-undo.button", "undo")}
      disabled={!available}
      icon={<Icon/>}
      onClick={available ? e => undo(e.nativeEvent) : null}
    />
  )
}

export default UndoButton
