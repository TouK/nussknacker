import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../../analytics/TrackingEvents"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {areAllModalsClosed} from "../../../selectors/ui"
import {redo} from "../../../../../actions/undoRedoActions"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getHistory} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function RedoButton(props: Props) {
  const {keyActionsAvailable, history, redo} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.edit.actions.redo.button", "redo")}
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
