import {css, cx} from "@emotion/css"
import {WindowButtonProps, WindowContentProps} from "@touk/window-manager"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useTranslation} from "react-i18next"
import {useDispatch, useSelector} from "react-redux"
import {loadProcessState} from "../../actions/nk"
import HttpService from "../../http/HttpService"
import {getProcessId} from "../../reducers/selectors/graph"
import {CustomAction} from "../../types"
import {UnknownRecord} from "../../types/common"
import {WindowContent} from "../../windowManager"
import {WindowKind} from "../../windowManager/WindowKind"
import {ChangeableValue} from "../ChangeableValue"
import {editors} from "../graph/node-modal/editors/expression/Editor"
import {ExpressionLang} from "../graph/node-modal/editors/expression/types"

interface CustomActionFormProps extends ChangeableValue<UnknownRecord> {
  action: CustomAction,
}

function CustomActionForm(props: CustomActionFormProps): JSX.Element {
  const {onChange, action} = props

  const empty = (action?.parameters || []).reduce((obj, param) => ({...obj, [param.name]: ""}), {})

  const [state, setState] = useState(empty)

  const setParam = (name: string) => (value: any) => setState(current => ({...current, [name]: value}))

  useEffect(
    () => onChange(state),
    [onChange, state],
  )

  return (
    <div className="node-table">
      {
        (action?.parameters || []).map(param => {
          const editorType = param.editor.type
          const Editor = editors[editorType]
          const fieldName = param.name
          return (
            <div className={"node-row"} key={param.name}>
              <div className="node-label" title={fieldName}>{fieldName}:</div>
              <Editor
                editorConfig={param?.editor}
                className={"node-value"}
                validators={[]}
                formatter={null}
                expressionInfo={null}
                onValueChange={setParam(fieldName)}
                expressionObj={{language: ExpressionLang.String, expression: state[fieldName]}}
                values={[]}
                readOnly={false}
                key={fieldName}
                showSwitch={false}
                showValidation={false}
                variableTypes={{}}
              />
            </div>
          )
        })
      }
    </div>
  )
}

export function CustomActionDialog(props: WindowContentProps<WindowKind, CustomAction>): JSX.Element {
  const processId = useSelector(getProcessId)
  const dispatch = useDispatch()
  const action = props.data.meta

  const [value, setValue] = useState<UnknownRecord>()

  const confirm = useCallback(async () => {
    await HttpService
      .customAction(processId, action.name, value)
      .finally(() => dispatch(loadProcessState(processId)))
    props.close()
  }, [processId, action.name, value, props, dispatch])

  const {t} = useTranslation()
  const buttons: WindowButtonProps[] = useMemo(
    () => [
      {title: t("dialog.button.cancel", "cancel"), action: () => props.close()},
      {title: t("dialog.button.confirm", "confirm"), action: () => confirm()},
    ],
    [confirm, props, t],
  )

  return (
    <WindowContent {...props} buttons={buttons}>
      <div className={cx("modalContentDark", css({padding: "1em", minWidth: 600}))}>
        <CustomActionForm action={action} value={value} onChange={setValue}/>
      </div>
    </WindowContent>
  )

}

export default CustomActionDialog
