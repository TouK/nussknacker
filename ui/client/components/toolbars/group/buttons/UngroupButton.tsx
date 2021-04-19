import React from "react"
import {getSelectedGroups} from "../../../../reducers/graph/utils"
import {useDispatch, useSelector} from "react-redux"
import NodeUtils from "../../../graph/NodeUtils"
import {ungroup, ungroupSelected} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getGraph, getNodeToDisplay} from "../../../../reducers/selectors/graph"
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

export function UngroupSelectedButton() {
  const graph = useSelector(getGraph)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.ungroup-selected.button", "ungroup")}
      icon={<Icon/>}
      disabled={!getSelectedGroups(graph).length}
      onClick={() => dispatch(ungroupSelected())}
    />
  )
}

export default UngroupButton
