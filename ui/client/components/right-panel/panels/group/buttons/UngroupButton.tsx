import React from "react"
import {RootState} from "../../../../../reducers/index"
import {connect} from "react-redux"
import InlinedSvgs from "../../../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../../../graph/NodeUtils"
import {ungroup} from "../../../../../actions/nk/groups"
import {ButtonWithIcon} from "../../../ButtonWithIcon"
import {getNodeToDisplay} from "../../../selectors/graph"
import {useTranslation} from "react-i18next"

type Props = StateProps

function UngroupButton(props: Props) {
  const {nodeToDisplay, ungroup} = props
  const {t} = useTranslation()

  return (
    <ButtonWithIcon
      name={t("panels.group.actions.ungroup.button", "ungroup")}
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
