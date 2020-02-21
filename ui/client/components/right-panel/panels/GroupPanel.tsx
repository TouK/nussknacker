/* eslint-disable i18next/no-literal-string */
import React from "react"
import {CapabilitiesType} from "../UserRightPanel"
import {RootState} from "../../../reducers/index"
import {connect} from "react-redux"
import {getNodeToDisplay, getGroupingState} from "../selectors"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../graph/NodeUtils"
import {cancelGrouping, ungroup, finishGrouping, startGrouping} from "../../../actions/nk/groups"
import {ButtonWithIcon} from "../ButtonWithIcon"
import cn from "classnames"
import {RightPanel} from "../RightPanel"

type OwnProps = {
  capabilities: CapabilitiesType,
}

type Props = OwnProps & StateProps

function GroupPanel(props: Props) {
  const {capabilities, nodeToDisplay, groupingState} = props
  const {cancelGrouping, finishGrouping, startGrouping, ungroup} = props

  const panelName = "Group"
  const buttons = [
    {
      name: "start",
      onClick: startGrouping,
      icon: InlinedSvgs.buttonGroup,
      disabled: groupingState != null,
      isHidden: !capabilities.write,
    },
    {
      name: "finish",
      onClick: finishGrouping,
      icon: InlinedSvgs.buttonGroup,
      disabled: (groupingState || []).length <= 1,
      isHidden: !capabilities.write,
    },
    {
      name: "cancel",
      onClick: cancelGrouping,
      icon: InlinedSvgs.buttonUngroup,
      disabled: !groupingState,
      isHidden: !capabilities.write,
    },
    {
      name: "ungroup",
      onClick: () => ungroup(nodeToDisplay),
      icon: InlinedSvgs.buttonUngroup,
      disabled: !NodeUtils.nodeIsGroup(nodeToDisplay),
      isHidden: !capabilities.write,
    },
  ]

  return (
    <RightPanel title={panelName}>
      {buttons.map(({name, isHidden, ...props}) => isHidden ? null : (
        <ButtonWithIcon
          {...props}
          key={name}
          name={name}
          title={name}
          className={cn("espButton", "right-panel")}
        />
      ))}
    </RightPanel>
  )
}

const mapState = (state: RootState) => ({
  groupingState: getGroupingState(state),
  nodeToDisplay: getNodeToDisplay(state),
})

const mapDispatch = {
  cancelGrouping,
  finishGrouping,
  startGrouping,
  ungroup,
}

type StateProps = typeof mapDispatch & ReturnType<typeof mapState>

export default connect(mapState, mapDispatch)(GroupPanel)
