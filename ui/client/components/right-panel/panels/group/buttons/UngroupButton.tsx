/* eslint-disable i18next/no-literal-string */
import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import {getNodeToDisplay} from "../../../selectors"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../../../graph/NodeUtils"
import {ungroup} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"

type Props = StateProps

function UngroupButton(props: Props) {
  const {nodeToDisplay, ungroup} = props

  return (
    <ButtonWithIcon
      name={"ungroup"}
      icon={InlinedSvgs.buttonUngroup}
      disabled={!NodeUtils.nodeIsGroup(nodeToDisplay)}
      onClick={() => ungroup(nodeToDisplay)}
    />
  )
}

const mapState = (state: RootState) => ({
  nodeToDisplay: getNodeToDisplay(state),
})

const mapDispatch = {
  ungroup,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(UngroupButton)
