/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {getHistory} from "../../../selectors"
import {areAllModalsClosed} from "../../../selectors-ui"
import {redo} from "../../../../../actions/undoRedoActions"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function RedoButton(props: Props) {
  const {keyActionsAvailable, history, redo} = props
  return (
    <ButtonWithIcon
      name={"redo"}
      disabled={history.future.length === 0}
      icon={InlinedSvgs.buttonRedo}
      onClick={() => keyActionsAvailable && redo({
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
  redo,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(RedoButton)
