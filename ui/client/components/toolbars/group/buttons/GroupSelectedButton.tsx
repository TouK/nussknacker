import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {groupSelected} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-finish.svg"
import {canGroupSelection} from "../../../../reducers/graph/utils"
import {getGraph} from "../../../../reducers/selectors/graph"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton"

export function GroupSelectedButton(): JSX.Element {
  const graph = useSelector(getGraph)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <ToolbarButton
      name={t("panels.actions.group-selected.button", "group selected")}
      icon={<Icon/>}
      disabled={!canGroupSelection(graph)}
      onClick={() => dispatch(groupSelected())}
    />
  )
}
