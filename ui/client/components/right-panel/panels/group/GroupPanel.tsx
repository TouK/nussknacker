import React, {memo} from "react"
import {CapabilitiesType} from "../../UserRightPanel"
import {RightToolPanel} from "../RightToolPanel"
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
    <RightToolPanel title={t("panels.group.title", "Group")}>
      {writeAllowed ? <GroupStart/> : null}
      {writeAllowed ? <GroupFinish/> : null}
      {writeAllowed ? <GroupCancel/> : null}
      {writeAllowed ? <Ungroup/> : null}
    </RightToolPanel>
  )
}

export default memo(GroupPanel)
