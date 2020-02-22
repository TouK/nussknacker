/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {startGrouping} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getGroupingState} from "../../../selectors/graph"

type Props = StateProps

function GroupStartButton(props: Props) {
  const {groupingState, startGrouping} = props

  return (
    <ButtonWithIcon
      name={"start"}
      icon={InlinedSvgs.buttonGroup}
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
