import React from "react"
import {RootState} from "../../../../reducers/index"
import {connect} from "react-redux"
import NodeUtils from "../../../graph/NodeUtils"
import {ungroup} from "../../../../actions/nk/groups"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"
import {getNodeToDisplay} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/ungroup.svg"

type Props = StateProps

function UngroupButton(props: Props) {
  const {nodeToDisplay, ungroup} = props
  const {t} = useTranslation()

  return (
    <ToolbarButton
      name={t("panels.actions.group-ungroup.button", "ungroup")}
      icon={<Icon/>}
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
