import React from "react"
import {CapabilitiesType} from "../../UserRightPanel"
import {RightPanel} from "../RightPanel"
import GroupStart from "./buttons/GroupStartButton"
import GroupFinish from "./buttons/GroupFinishButton"
import Ungroup from "./buttons/UngroupButton"
import GroupCancel from "./buttons/GroupCancelButton"
import {useTranslation} from "react-i18next"

type Props = {
  capabilities: CapabilitiesType,
}

function GroupPanel(props: Props) {
  const {capabilities} = props
  const {t} = useTranslation()

  const writeAllowed = capabilities.write

  return (
    <RightPanel title={t("panels.group.title", "Group")}>
      {writeAllowed ? <GroupStart/> : null}
      {writeAllowed ? <GroupFinish/> : null}
      {writeAllowed ? <GroupCancel/> : null}
      {writeAllowed ? <Ungroup/> : null}
    </RightPanel>
  )
}

export default GroupPanel
