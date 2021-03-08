import {UnknownFunction} from "../../../../../types/common"
import {DualEditorMode, editors, SimpleEditor} from "./Editor"
import SwitchIcon from "./SwitchIcon"
import React, {useState} from "react"
import {ExpressionObj} from "./types"
import RawEditor from "./RawEditor"
import {VariableTypes} from "../../../../../types"

type Props = {
  editorConfig: $TodoType,
  expressionObj: ExpressionObj,
  readOnly: boolean,
  valueClassName: string,

  validators: Array<$TodoType>,
  isMarked: boolean,
  showValidation: boolean,
  onValueChange: UnknownFunction,
  className: string,
  variableTypes: VariableTypes,
  showSwitch: boolean,
}

export default function DualParameterEditor(props: Props) {
  const {editorConfig, readOnly, valueClassName, expressionObj} = props
  const SimpleEditor = editors[editorConfig.simpleEditor.type] as SimpleEditor
  const showSwitch = props.showSwitch && SimpleEditor
  const simpleEditorAllowsSwitch = SimpleEditor?.switchableTo(expressionObj, editorConfig.simpleEditor)

  const initialDisplaySimple = editorConfig.defaultMode === DualEditorMode.SIMPLE && simpleEditorAllowsSwitch
  const [displayRawEditor, setDisplayRawEditor] = useState(!initialDisplaySimple)

  const switchable = !displayRawEditor || simpleEditorAllowsSwitch
  const hint = simpleEditorAllowsSwitch ? SimpleEditor?.switchableToHint() : SimpleEditor?.notSwitchableToHint()

  const editorProps = {
    ...props,
    className: `${valueClassName ? valueClassName : "node-value"} ${showSwitch ? "switchable" : ""}`,
  }
  return (
    <>
      {displayRawEditor ?
        (<RawEditor {...editorProps}/>) :
        (<SimpleEditor {...editorProps} editorConfig={editorConfig.simpleEditor}/>)
      }
      {showSwitch ?
        (
          <SwitchIcon
            switchable={switchable}
            hint={hint}
            onClick={(_) => setDisplayRawEditor(!displayRawEditor)}
            displayRawEditor={displayRawEditor}
            readOnly={readOnly}
          />
        ) :
        null}
    </>
  )
}
