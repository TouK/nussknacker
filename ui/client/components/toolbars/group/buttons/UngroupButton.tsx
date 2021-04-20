import React from "react"
import {useDispatch, useSelector} from "react-redux"
import NodeUtils from "../../../graph/NodeUtils"
import {ungroup} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getNodeToDisplay} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/ungroup.svg"

function UngroupButton(): JSX.Element {
  const nodeToDisplay = useSelector(getNodeToDisplay)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-ungroup.button", "ungroup")}
      icon={<Icon/>}
      disabled={!NodeUtils.nodeIsGroup(nodeToDisplay)}
      onClick={() => dispatch(ungroup(nodeToDisplay))}
    />
  )
}

export default UngroupButton
