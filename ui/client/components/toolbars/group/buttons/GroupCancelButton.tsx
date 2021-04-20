import React from "react"
import {useDispatch, useSelector} from "react-redux"
import {cancelGrouping} from "../../../../actions/nk/groups"
import {CapabilitiesToolbarButton} from "../../../toolbarComponents/CapabilitiesToolbarButton"
import {getGroupingState} from "../../../../reducers/selectors/graph"
import {useTranslation} from "react-i18next"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/group-cancel.svg"

function GroupFinishButton(): JSX.Element {
  const groupingState = useSelector(getGroupingState)
  const {t} = useTranslation()
  const dispatch = useDispatch()

  return (
    <CapabilitiesToolbarButton
      write
      name={t("panels.actions.group-cancel.button", "cancel")}
      icon={<Icon/>}
      disabled={!groupingState}
      onClick={() => dispatch(cancelGrouping())}
    />
  )
}

export default GroupFinishButton
