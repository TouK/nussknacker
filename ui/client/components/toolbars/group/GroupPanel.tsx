import React from "react"
import {CollapsibleToolbar} from "../../toolbarComponents/CollapsibleToolbar"
import GroupStart from "./buttons/GroupStartButton"
import GroupFinish from "./buttons/GroupFinishButton"
import Ungroup from "./buttons/UngroupButton"
import GroupCancel from "./buttons/GroupCancelButton"
import {useTranslation} from "react-i18next"
import {ToolbarButtons} from "../../toolbarComponents/ToolbarButtons"

function GroupPanel(): JSX.Element {
  const {t} = useTranslation()

  return (
    <CollapsibleToolbar id="GROUP-PANEL" title={t("panels.group.title", "Group")}>
      <ToolbarButtons>
        <GroupStart/>
        <GroupFinish/>
        <GroupCancel/>
        <Ungroup/>
      </ToolbarButtons>
    </CollapsibleToolbar>
  )
}

export default GroupPanel
