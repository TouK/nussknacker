/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {areAllModalsClosed} from "../../../../../reducers/selectors/ui"
import {undo} from "../../../../../actions/undoRedoActions"
import {ToolbarButton} from "../../../ToolbarButton"
import {getHistory} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function UndoButton(props: Props) {
  const {keyActionsAvailable, undo, history} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-undo.button", "undo")}
      disabled={history.past.length === 0}
      icon={InlinedSvgs.buttonUndo}
      onClick={() => keyActionsAvailable && undo({
        category: events.categories.rightPanel,
        action: events.actions.buttonClick,
      })}
    />
  )
}

const mapState = (state: RootState) => ({
  keyActionsAvailable: areAllModalsClosed(state),
  history: getHistory(state),
})

const mapDispatch = {
  undo,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(UndoButton)
