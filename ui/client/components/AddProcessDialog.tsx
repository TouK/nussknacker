import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {visualizationUrl} from "../common/VisualizationUrl"
import {useProcessNameValidators} from "../containers/hooks/useProcessNameValidators"
import history from "../history"
import HttpService from "../http/HttpService"
import "../stylesheets/visualization.styl"
import {WindowContent} from "../windowManager"
import {AddProcessForm} from "./AddProcessForm"
import {allValid} from "./graph/node-modal/editors/Validators"

interface AddProcessDialogProps extends WindowContentProps {
  isSubprocess?: boolean,
}

export function AddProcessDialog(props: AddProcessDialogProps): JSX.Element {
  const {isSubprocess, ...passProps} = props
  const nameValidators = useProcessNameValidators()

  const [value, setState] = useState({processId: "", processCategory: ""})

  const isValid = useMemo(
    () => value.processCategory && allValid(nameValidators, [value.processId]),
    [nameValidators, value],
  )

  const createProcess = useCallback(
    async () => {
      if (isValid) {
        const {processId, processCategory} = value
        await HttpService.createProcess(processId, processCategory, isSubprocess)
        passProps.close()
        history.push(visualizationUrl(processId))
      }
    },
    [isSubprocess, isValid, passProps, value],
  )

  const {t} = useTranslation()
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t("dialog.button.cancel", "Cancel"), action: () => passProps.close()},
      {title: t("dialog.button.create", "create"), action: () => createProcess(), disabled: !isValid},
    ],
    [createProcess, isValid, passProps, t],
  )

  return (
    <WindowContent buttons={buttons} {...passProps}>
      <AddProcessForm
        value={value}
        onChange={setState}
        nameValidators={nameValidators}
      />
    </WindowContent>
  )
}

