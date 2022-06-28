import {UnknownFunction} from "../../../../../types/common"
import {DualEditorMode, editors, SimpleEditor} from "./Editor"
import SwitchIcon from "./SwitchIcon"
import React, {useMemo, useState} from "react"
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

export default function DualParameterEditor(props: Props): JSX.Element {
  const {editorConfig, readOnly, valueClassName, expressionObj} = props

  const SimpleEditor = useMemo(
    () => editors[editorConfig.simpleEditor.type] as SimpleEditor<{
      onValueChange: (value: string) => void,
      editorConfig?: unknown,
    }>,
    [editorConfig.simpleEditor.type]
  )

  const showSwitch = useMemo(
    () => props.showSwitch && SimpleEditor,
    [SimpleEditor, props.showSwitch]
  )

  const simpleEditorAllowsSwitch = useMemo(
    () => SimpleEditor?.switchableTo(expressionObj, editorConfig.simpleEditor),
    [SimpleEditor, editorConfig.simpleEditor, expressionObj]
  )

  const initialDisplaySimple = useMemo(
    () => editorConfig.defaultMode === DualEditorMode.SIMPLE && simpleEditorAllowsSwitch,
    [editorConfig.defaultMode, simpleEditorAllowsSwitch]
  )

  const [displayRawEditor, setDisplayRawEditor] = useState(!initialDisplaySimple)

  const switchable = useMemo(
    () => !displayRawEditor || simpleEditorAllowsSwitch,
    [displayRawEditor, simpleEditorAllowsSwitch]
  )

  const hint = useMemo(
    () => simpleEditorAllowsSwitch ? SimpleEditor?.switchableToHint() : SimpleEditor?.notSwitchableToHint(),
    [SimpleEditor, simpleEditorAllowsSwitch]
  )

  const editorProps = useMemo(
    () => ({
      ...props,
      className: `${valueClassName ? valueClassName : "node-value"} ${showSwitch ? "switchable" : ""}`,
    }),
    [props, showSwitch, valueClassName]
  )

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
