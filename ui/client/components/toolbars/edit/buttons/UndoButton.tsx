/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../reducers/index"
import {useDispatch, useSelector} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {areAllModalsClosed} from "../../../../reducers/selectors/ui"
import {undo} from "../../../../actions/undoRedoActions"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/undo.svg"

function UndoButton(): JSX.Element {
  const {keyActionsAvailable, history} = useSelector(mapState)
  const {t} = useTranslation()
  const dispatch = useDispatch()
  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.edit-undo.button", "undo")}
      disabled={!history.past.length}
      icon={<Icon/>}
      onClick={() => keyActionsAvailable && dispatch(undo({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
      }))}
    />
  )
}

const mapState = (state: RootState) => ({
  keyActionsAvailable: areAllModalsClosed(state),
  history: getHistory(state),
})

export default UndoButton
