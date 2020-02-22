/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import {finishGrouping} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getGroupingState} from "../../../selectors/graph"

type Props = StateProps

function GroupFinishButton(props: Props) {
  const {groupingState, finishGrouping} = props

  return (
    <ButtonWithIcon
      name={"finish"}
      icon={InlinedSvgs.buttonGroup}
      disabled={(groupingState || []).length <= 1}
      onClick={finishGrouping}
    />
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
})

const mapDispatch = {
  finishGrouping,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupFinishButton)
