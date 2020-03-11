import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import {events} from "../../../../analytics/TrackingEvents"
import {areAllModalsClosed} from "../../../../reducers/selectors/ui"
import {redo} from "../../../../actions/undoRedoActions"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getHistory} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/redo.svg"

type Props = StateProps

function RedoButton(props: Props) {
  const {keyActionsAvailable, history, redo} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.edit-redo.button", "redo")}
      disabled={history.future.length === 0}
      icon={<Icon/>}
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
