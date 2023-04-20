import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/test-with-schema.svg"
import {
  getProcessId, getProcessToDisplay,
  getTestCapabilities, getTestViewParameters,
  isLatestProcessVersion
} from "../../../../reducers/selectors/graph"
import {useWindows, WindowKind} from "../../../../windowManager"
import {ToolbarButtonProps} from "../../types"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import _ from "lodash"
import {TestViewParameters} from "../../../../common/TestResultUtils";
import {testProcessWithParameters} from "../../../../actions/nk/displayTestResults";
import {GenericActionParameters} from "../../../modals/GenericActionDialog";
import {UIValueParameter} from "../../../../actions/nk/genericAction";
import {Expression} from "../../../../types";
import {SourceWithParametersTest} from "../../../../http/HttpService";
import {getFindAvailableVariables} from "../../../graph/node-modal/NodeDetailsContent/selectors";

type Props = ToolbarButtonProps

function TestWithSchemaButton(props: Props) {
  const {disabled} = props
  const {t} = useTranslation()
  const {open} = useWindows()
  const processIsLatestVersion = useSelector(isLatestProcessVersion)
  const testCapabilities = useSelector(getTestCapabilities)
  const testViewParameters: TestViewParameters[] = useSelector(getTestViewParameters)
  const processId = useSelector(getProcessId)
  const processToDisplay = useSelector(getProcessToDisplay)
  const findAvailableVariables = useSelector(getFindAvailableVariables)
  const dispatch = useDispatch()

  const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canCreateTestView

  const [action, setAction] = useState(null)
  const [selectedSource, setSelectedSource] = useState(_.head(testViewParameters)?.sourceId)
  const [sourceParameters, setSourceParameters] = useState(updateParametersFromTestView())
  const variableTypes = useMemo(() => findAvailableVariables?.(selectedSource), [findAvailableVariables, selectedSource])

  function updateParametersFromTestView(): {[key: string]: GenericActionParameters} {
    return (testViewParameters || []).reduce((testViewObj, testViewParam) => ({
      ...testViewObj,
      [testViewParam.sourceId]: {
        parameters: testViewParam.parameters,
        parametersValues: (testViewParam.parameters || []).reduce((paramObj, param) => ({
          ...paramObj,
          [param.name]: param.defaultValue,
        }), {}),
        onParamUpdate: (name: string) => (value: any) => onParamUpdate(testViewParam.sourceId, name, value)
      }
    }), {})
  }

  function onParamUpdate(sourceId: string, name: string, value: any) {
    setSourceParameters(current => ({
      ...current,
      [sourceId]: {
        ...current[sourceId],
        parametersValues: {
          ...current[sourceId].parametersValues,
          [name]: {expression: value, language: current[sourceId].parametersValues[name].language}
        }
      }
    }))
  }

  const onConfirmAction = useCallback((paramValues) => {
    const parameters: {[paramName: string]: Expression} = sourceParameters[selectedSource].parameters.reduce((obj, uiParam) => ({
      ...obj,
      [uiParam.name]: paramValues[uiParam.name]
    }), {})
    const request: SourceWithParametersTest = {sourceId: selectedSource as string, parameters: parameters}
    dispatch(testProcessWithParameters(processId, request, processToDisplay))
  }, [sourceParameters, selectedSource])

  //For now, we select first source and don't provide way to change it
  //Add support for multiple sources in next iteration (?)
  useEffect(() => {
    setSelectedSource(_.head(testViewParameters)?.sourceId);
    setSourceParameters(updateParametersFromTestView());
  }, [testViewParameters]);

  useEffect(() => {
    setAction({
      variableTypes: variableTypes,
      layout: {
        name: "Test",
        confirmText: "Test"
      },
      ...sourceParameters[selectedSource],
      onConfirmAction
    });
  }, [testViewParameters, sourceParameters, selectedSource]);

  return (
    <ToolbarButton
      name={t("panels.actions.test-with-schema.button", "test window")}
      icon={<Icon/>}
      disabled={!available || disabled}
      onClick={() => {
        open({
          title: t("dialog.title.testWithSchema", "Test scenario"),
          isResizable: true,
          kind: WindowKind.genericAction,
          meta: action,
        })
      }}
    />
  )

}

export default TestWithSchemaButton
