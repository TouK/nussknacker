import React, {memo} from "react"
import {CollapsibleToolbar} from "../../toolbars/CollapsibleToolbar"
import GroupStart from "./buttons/GroupStartButton"
import GroupFinish from "./buttons/GroupFinishButton"
import Ungroup from "./buttons/UngroupButton"
import GroupCancel from "./buttons/GroupCancelButton"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {getCapabilities} from "../../selectors/other"
import {ToolbarButtons} from "../../../Process/ToolbarButtons"

function GroupPanel() {
  const capabilities = useSelector(getCapabilities)
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="GROUP-PANEL" title={t("panels.group.title", "Group")}>
      <ToolbarButtons>
        {capabilities.write ? <GroupStart/> : null}
        {capabilities.write ? <GroupFinish/> : null}
        {capabilities.write ? <GroupCancel/> : null}
        {capabilities.write ? <Ungroup/> : null}
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default memo(GroupPanel)
