import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {ungroup} from "../../../../actions/nk/groups"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/ungroup.svg"
import {getNodeToDisplay} from "../../../../reducers/selectors/graph"
import NodeUtils from "../../../graph/NodeUtils"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

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
