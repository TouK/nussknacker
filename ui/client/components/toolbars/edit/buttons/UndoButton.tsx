/* eslint-disable i18next/no-literal-string */
import React from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {undo} from "../../../../actions/undoRedoActions"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/undo.svg"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useSelectionActions} from "../../../graph/SelectionContextProvider"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

function UndoButton(): JSX.Element {
  const {undo} = useSelectionActions()
  const history = useSelector(getHistory)
  const {t} = useTranslation()
  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-undo.button", "undo")}
      disabled={!history.past.length || !undo}
      icon={<Icon/>}
      onClick={undo ? e => undo(e.nativeEvent) : null}
    />
  )
}

export default UndoButton
