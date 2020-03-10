import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import * as InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../../../graph/NodeUtils"
import {ungroup} from "../../../../../actions/nk/groups"
import ToolbarButton from "../../../ToolbarButton"
import {getNodeToDisplay} from "../../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function UngroupButton(props: Props) {
  const {nodeToDisplay, ungroup} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.group-ungroup.button", "ungroup")}
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
