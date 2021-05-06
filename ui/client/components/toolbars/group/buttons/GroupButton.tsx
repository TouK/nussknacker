import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {groupSelected} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-finish.svg"
import {canGroupSelection} from "../../../../reducers/graph/utils"
import {getGraph} from "../../../../reducers/selectors/graph"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

export function GroupButton(): JSX.Element {
  const graph = useSelector(getGraph)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-selected.button", "group")}
      icon={<Icon/>}
      disabled={!canGroupSelection(graph)}
      onClick={() => dispatch(groupSelected())}
    />
  )
}
