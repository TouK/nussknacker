import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import HttpService from "../../http/HttpService"
import {getProcessId, getProcessToDisplay} from "../../reducers/selectors/graph"
import {getFeatureSettings} from "../../reducers/selectors/settings"
import "../../stylesheets/visualization.styl"
import {PromptContent} from "../../windowManager"
import {
  literalIntegerValueValidator,
  mandatoryValueValidator,
  maximalNumberValidator,
  minimalNumberValidator,
} from "../graph/node-modal/editors/Validators"
import {InputWithFocus} from "../withFocus"
import ValidationLabels from "./ValidationLabels"

function GenerateTestDataDialog(props: WindowContentProps): JSX.Element {
  const {t} = useTranslation()
  const processId = useSelector(getProcessId)
  const processToDisplay = useSelector(getProcessToDisplay)
  const maxSize = useSelector(getFeatureSettings).testDataSettings.maxSamplesCount

  const [{testSampleSize}, setState] = useState({
    //TODO: current validators work well only for string values
    testSampleSize: "10",
  })

  const confirmAction = useCallback(
    async () => {
      await HttpService.generateTestData(processId, testSampleSize, processToDisplay)
      props.close()
    },
    [processId, processToDisplay, props, testSampleSize],
  )

  const validators = [literalIntegerValueValidator, minimalNumberValidator(0), maximalNumberValidator(maxSize), mandatoryValueValidator]
  const isValid = validators.every(v => v.isValid(testSampleSize))

  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t("dialog.button.cancel", "Cancel"), action: () => props.close()},
      {title: t("dialog.button.ok", "Ok"), disabled: !isValid, action: () => confirmAction()},
    ],
    [t, confirmAction, props, isValid],
  )

  return (
    <PromptContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark", css({minWidth: 400}))}>
        <h3>{t("test-generate.title", "Generate test data")}</h3>
        <InputWithFocus
          value={testSampleSize}
          onChange={(event) => setState({testSampleSize: event.target.value})}
          className={css({
            minWidth: "100%",
          })}
          autoFocus
        />
        <ValidationLabels validators={validators} values={[testSampleSize]}/>
      </div>
    </PromptContent>
  )
}

export default GenerateTestDataDialog
