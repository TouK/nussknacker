import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {getProcessId} from "../../reducers/selectors/graph"
import {Expression, UIParameter} from "../../types"
import {WindowContent} from "../../windowManager"
import {WindowKind} from "../../windowManager"
import {editors, simpleEditorValidators} from "../graph/node-modal/editors/expression/Editor"
import {NodeTable, NodeTableBody} from "../graph/node-modal/NodeDetailsContent/NodeTable"
import {ContentSize} from "../graph/node-modal/node/ContentSize";
import {FieldLabel} from "../graph/node-modal/FieldLabel";
import {validateGenericActionParameters} from "../../actions/nk/genericAction";
import {getProcessProperties} from "../graph/node-modal/NodeDetailsContent/selectors";
import {getGenericActionValidation} from "../../reducers/selectors/genericActionState";

//TODO
// - Custom action execution - Properties as in Custom Actions or we are doing it different way it is should be defined "in code"?
// - Granulate it
export type GenericActionLayout = {
  name: string,
  icon?: string,
  confirmText?: string,
  cancelText?: string,
}

export interface GenericActionParameters {
  parameters?: UIParameter[],
  parametersValues: {[key:string]: Expression},
  onParamUpdate?: (name: string) => (value: any) => void,
}

interface GenericAction extends GenericActionParameters {
  layout: GenericActionLayout,
  onConfirmAction: (parmValues) => void
}

interface GenericActionDialogProps {
  action: GenericAction,
  setIsValid: (boolean) => void
  value: {[p: string]: Expression}
  setValue: (value: (((prevState: {[p: string]: Expression}) => {[p: string]: Expression}) | {[p: string]: Expression})) => void
}

function GenericActionForm(props: GenericActionDialogProps): JSX.Element {
  const {value, setValue, action, setIsValid} = props
  const dispatch = useDispatch()
  const validationResult = useSelector(getGenericActionValidation)
  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)
  const [validators, setValidators] = useState({})

  const setParam = useCallback(
    (name: string) => (value: any) => {
      action.onParamUpdate(name)(value)
      setValue(current => ({...current, [name]: {expression: value, language: current[name].language}}))
    }, [dispatch, value])

  useEffect(() => {
    const parameterValidators = action?.parameters.reduce((object, uiParam) => {
      const paramValidators = simpleEditorValidators(uiParam, validationResult.validationErrors, uiParam.name, uiParam.name)
      return {...object, [uiParam.name]: paramValidators}
    }, {})
    const areParamsValid = Object.keys(parameterValidators)
      .reduce((a, name) => a && parameterValidators[name].every(v => v.isValid(value[name].expression)), true)
    setIsValid(areParamsValid)
    setValidators(parameterValidators)
  }, [value, validationResult, dispatch, setValue])

  useEffect(() => {
    dispatch(validateGenericActionParameters(processId, {
      parameters: action.parameters.map(uiParam => {
        return {
          name: uiParam.name,
          typ: uiParam.typ,
          expression: value[uiParam.name]
        }
      }),
      processProperties: processProperties,
      variableTypes: {}
    }))
  }, [value])

  useEffect(
    () => setValue(value),
    [setValue, value],
  )
  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeTable>
          <NodeTableBody>
          {
            (action?.parameters || []).map(param => {
              const Editor = editors[param.editor.type]
              const fieldName = param.name
              return (
                <div className={"node-row"} key={param.name}>
                  <FieldLabel
                    nodeId={param.name}
                    parameterDefinitions={action.parameters}
                    paramName={param.name}
                  />
                  <Editor
                    editorConfig={param?.editor}
                    className={"node-value"}
                    validators={validators[fieldName]}
                    formatter={null}
                    expressionInfo={null}
                    onValueChange={setParam(fieldName)}
                    expressionObj={value[fieldName]}
                    values={[]}
                    readOnly={false}
                    key={fieldName}
                    showSwitch={false}
                    showValidation={true}
                    variableTypes={{}}
                  />
                </div>
              )
            })
          }
          </NodeTableBody>
        </NodeTable>
      </ContentSize>
    </div>
  )
}

export function GenericActionDialog(props: WindowContentProps<WindowKind, GenericAction>): JSX.Element {
  const processId = useSelector(getProcessId)
  const {t} = useTranslation()
  const action = props.data.meta
  const [value, setValue] = useState(() => (action?.parameters || []).reduce((obj, param) => ({
    ...obj,
    [param.name]: action.parametersValues[param.name],
  }), {}))
  const [isValid, setIsValid] = useState(false)

  const confirm = useCallback(async () => {
    action.onConfirmAction(value)
    props.close()
  }, [processId, action.layout.name, value, props])
  const cancelText = action.layout.cancelText ? action.layout.cancelText : "cancel"
  const confirmText = action.layout.confirmText ? action.layout.confirmText : "confirm"
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t(`dialog.generic.button.${cancelText}`, cancelText), action: () => props.close()},
      {title: t(`dialog.generic.button.${confirmText}`, confirmText), action: () => confirm(), disabled: !isValid},
    ],
    [confirm, props, t],
  )

  return (
    <WindowContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark", css({padding: "1em", minWidth: 600}))}>
        <GenericActionForm action={action} setIsValid={setIsValid} value={value} setValue={setValue}/>
      </div>
    </WindowContent>
  )

}

export default GenericActionDialog
