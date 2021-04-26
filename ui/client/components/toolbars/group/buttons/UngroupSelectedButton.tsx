import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {ungroupSelected} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/ungroup.svg"
import {getSelectedGroups} from "../../../../reducers/graph/utils"
import {getGraph} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

export function UngroupSelectedButton(): JSX.Element {
  const graph = useSelector(getGraph)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <ToolbarButton
      name={t("panels.actions.ungroup-selected.button", "ungroup selected")}
      icon={<Icon/>}
      disabled={!getSelectedGroups(graph).length}
      onClick={() => dispatch(ungroupSelected())}
    />
  )
}
