import React from "react"
import {useDispatch, useSelector} from "react-redux"
import {finishGrouping, groupSelected} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getGraph, getGroupingState} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-finish.svg"
import {canGroupSelection} from "../../../../reducers/graph/utils"

export function GroupFinishButton(): JSX.Element {
  const groupingState = useSelector(getGroupingState)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-finish.button", "finish")}
      icon={<Icon/>}
      disabled={(groupingState || []).length <= 1}
      onClick={() => dispatch(finishGrouping())}
    />
  )
}

export function GroupSelectedButton(): JSX.Element {
  const graph = useSelector(getGraph)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-selected.button", "group selected")}
      icon={<Icon/>}
      disabled={!canGroupSelection(graph)}
      onClick={() => dispatch(groupSelected())}
    />
  )
}

export default GroupFinishButton
