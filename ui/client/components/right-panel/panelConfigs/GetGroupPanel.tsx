/* eslint-disable i18next/no-literal-string */
import {PanelConfig} from "../PanelConfig"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeUtils from "../../graph/NodeUtils"
import {OwnProps as PanelOwnProps} from "../UserRightPanel"
import {Props} from "../Panels1"

type A = Pick<PanelOwnProps & Props,
  | "capabilities"
  | "nodeToDisplay"
  | "groupingState"
  | "actions">

export function getGroupPanel(props: A): PanelConfig {
  const {capabilities, nodeToDisplay, groupingState, actions} = props

  return {
    panelName: "Group",
    buttons: [
      {
        name: "start",
        onClick: actions.startGrouping,
        icon: InlinedSvgs.buttonGroup,
        disabled: groupingState != null,
        isHidden: !capabilities.write,
      },
      {
        name: "finish",
        onClick: actions.finishGrouping,
        icon: InlinedSvgs.buttonGroup,
        disabled: (groupingState || []).length <= 1,
        isHidden: !capabilities.write,
      },
      {
        name: "cancel",
        onClick: actions.cancelGrouping,
        icon: InlinedSvgs.buttonUngroup,
        disabled: !groupingState,
        isHidden: !capabilities.write,
      },
      {
        name: "ungroup",
        onClick: () => actions.ungroup(nodeToDisplay),
        icon: InlinedSvgs.buttonUngroup,
        disabled: !NodeUtils.nodeIsGroup(nodeToDisplay),
        isHidden: !capabilities.write,
      },
    ],
  }
}
