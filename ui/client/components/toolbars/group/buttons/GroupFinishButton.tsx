import React from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {finishGrouping} from "../../../../actions/nk"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-finish.svg"
import {getGroupingState} from "../../../../reducers/selectors/graph"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"

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

export default GroupFinishButton
