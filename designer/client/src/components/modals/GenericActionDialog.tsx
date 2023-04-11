import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {getProcessId} from "../../reducers/selectors/graph"
import {UIParameter} from "../../types"
import {UnknownRecord} from "../../types/common"
import {WindowContent} from "../../windowManager"
import {WindowKind} from "../../windowManager"
import {ChangeableValue} from "../ChangeableValue"
import {editors, simpleEditorValidators} from "../graph/node-modal/editors/expression/Editor"
import {NodeTable, NodeTableBody} from "../graph/node-modal/NodeDetailsContent/NodeTable"
import {ContentSize} from "../graph/node-modal/node/ContentSize";
import {FieldLabel} from "../graph/node-modal/FieldLabel";
import {getGenericActionValidation} from "../../reducers/selectors/genericActionState";
import {validateGenericActionParameters} from "../../actions/nk/genericAction";
import {getProcessProperties} from "../graph/node-modal/NodeDetailsContent/selectors";

//TODO
// - Custom action execution - Properties as in Custom Actions or we are doing it different way it is should be defined "in code"?
// - Granulate it

export type GenericActionLayout = {
  name: string,
  icon?: string,
  confirmText?: string,
  cancelText?: string,
}

export type GenericAction = {
  layout: GenericActionLayout,
  onParamUpdate?: (name: string) => (value: any) => void,
  parameters?: UIParameter[],
  parametersValues: {[key:string]:$TodoType}
}


interface GenericActionDialogProps extends ChangeableValue<UnknownRecord> {
  action: GenericAction,
}

function GenericActionForm(props: GenericActionDialogProps): JSX.Element {
  const {onChange, action} = props
  const dispatch = useDispatch()
  const validationResult = useSelector(getGenericActionValidation)
  const processId = useSelector(getProcessId)
  const processProperties = useSelector(getProcessProperties)

  const [state, setState] = useState(() => (action?.parameters || []).reduce((obj, param) => ({
    ...obj,
    [param.name]: action.parametersValues[param.name],
  }), {}))

  const setParam = useCallback(
    (name: string) => (value: any) => {
      action.onParamUpdate(name)(value)
      setState(current => ({...current,
        errors: {[name]: validationResult},
        [name]: {expression: value, language: current[name].language}}))
    },
    []
  )

  useEffect(() => {
    dispatch(validateGenericActionParameters(processId, {
      parameters: action.parameters.map(uiParam => {
        return {
          name: uiParam.name,
          typ: uiParam.typ,
          expression: state[uiParam.name]
        }
      }),
      processProperties: processProperties,
      variableTypes: {}
    }))
  }, [state])

  useEffect(
    () => onChange(state),
    [onChange, state],
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
              const validators = simpleEditorValidators(param, validationResult.validationErrors, fieldName, fieldName)
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
                    validators={validators}
                    formatter={null}
                    expressionInfo={null}
                    onValueChange={setParam(fieldName)}
                    expressionObj={state[fieldName]}
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
  const dispatch = useDispatch()
  const action = props.data.meta
  const [value, setValue] = useState<UnknownRecord>()

  const confirm = useCallback(async () => {
    //TODO
    props.close()
  }, [processId, action.layout.name, value, props, dispatch])

  const {t} = useTranslation()
  const cancelText = action.layout.cancelText ? action.layout.cancelText : "cancel"
  const confirmText = action.layout.confirmText ? action.layout.confirmText : "confirm"
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t(`dialog.generic.button.${cancelText}`, cancelText), action: () => props.close()},
      {title: t(`dialog.generic.button.${confirmText}`, confirmText), action: () => confirm()},
    ],
    [confirm, props, t],
  )

  return (
    <WindowContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark", css({padding: "1em", minWidth: 600}))}>
        <GenericActionForm action={action} value={value} onChange={setValue}/>
      </div>
    </WindowContent>
  )

}

export default GenericActionDialog
