import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {startGrouping} from "../../../../../actions/nk/groups"
import {ToolbarButton} from "../../../ToolbarButton"
import {getGroupingState} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function GroupStartButton(props: Props) {
  const {groupingState, startGrouping} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.group-start.button", "start")}
      icon={InlinedSvgs.buttonGroupStart}
      disabled={groupingState != null}
      onClick={startGrouping}
    />
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
})

const mapDispatch = {
  startGrouping,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupStartButton)
