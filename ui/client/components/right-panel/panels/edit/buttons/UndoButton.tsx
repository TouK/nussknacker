/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {getHistory} from "../../../selectors"
import {areAllModalsClosed} from "../../../selectors-ui"
import {undo} from "../../../../../actions/undoRedoActions"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function UndoButton(props: Props) {
  const {keyActionsAvailable, undo, history} = props
  return (
    <ButtonWithIcon
      name={"undo"}
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
