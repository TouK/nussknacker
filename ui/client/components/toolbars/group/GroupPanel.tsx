import React, {memo} from "react"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import GroupStart from "./buttons/GroupStartButton"
import GroupFinish from "./buttons/GroupFinishButton"
import Ungroup from "./buttons/UngroupButton"
import GroupCancel from "./buttons/GroupCancelButton"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../../reducers/selectors/other"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

function GroupPanel() {
  const {write} = useSelector(getCapabilities)
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="GROUP-PANEL" title={t("panels.group.title", "Group")}>
      <ToolbarButtons>
        {write ? <GroupStart/> : null}
        {write ? <GroupFinish/> : null}
        {write ? <GroupCancel/> : null}
        {write ? <Ungroup/> : null}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(GroupPanel)
