import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {redo} from "../../../../actions/undoRedoActions"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/redo.svg"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

function RedoButton(): JSX.Element {
  const {redo} = useSelectionActions()
  const history = useSelector(getHistory)
  const {t} = useTranslation()
  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-redo.button", "redo")}
      disabled={!history.future.length || !redo}
      icon={<Icon/>}
      onClick={redo ? e => redo(e.nativeEvent) : null}
    />
  )
}

export default RedoButton
