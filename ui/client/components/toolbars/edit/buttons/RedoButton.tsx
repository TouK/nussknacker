import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {redo} from "../../../../actions/undoRedoActions"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/redo.svg"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {ToolbarButtonProps} from "../../types"

function RedoButton(props: ToolbarButtonProps): JSX.Element {
  const {redo} = useSelectionActions()
  const history = useSelector(getHistory)
  const {t} = useTranslation()
  const {disabled} = props
  const available = !disabled && history.future.length > 0 && redo

  return (
    <CapabilitiesToolbarButton
      editFrontend
      name={t("panels.actions.edit-redo.button", "redo")}
      disabled={!available}
      icon={<Icon/>}
      onClick={available ? e => redo(e.nativeEvent) : null}
    />
  )
}

export default RedoButton
