import {ExpressionObj} from "../types"
import {Validator} from "../../Validators"
import React, {useState} from "react"
import Cron from 'react-cron-generator'
import "react-cron-generator/dist/cron-builder.css"
import Input from "../../field/Input"
import "./cronEditorStyle.styl"

type CronExpression = string

type Props = {
  expressionObj: ExpressionObj,
  onValueChange: Function,
  validators: Array<Validator>,
  showValidation?: boolean,
  readOnly: boolean,
  isMarked: boolean,
}

const CRON_DECODE_REGEX = /T\(com\.cronutils\.parser\.CronParser\)\.parse\('(.*?)'\)/

export default function CronEditor(props: Props) {

  const {expressionObj, validators, isMarked} = props

  function decode(expression: string): CronExpression {
    return CRON_DECODE_REGEX.exec(expression)[1]
  }

  const [value, setValue] = useState(decode(expressionObj.expression))

  return (
    <div className={"cron-editor-container"}>
      <Input
        value={value}
        validators={validators}
        isMarked={isMarked}
      />
      <Cron
        onChange={(e) => {
          setValue(e)
        }}
        value={value}
        showResultText={true}
        showResultCron={true}
      />

    </div>
  )
}

CronEditor.switchableTo = (expressionObj: ExpressionObj) => CRON_DECODE_REGEX.test(expressionObj.expression)
CronEditor.switchableToHint = "Switch to basic mode"
CronEditor.notSwitchableToHint = "Expression must match pattern T(com.cronutils.parser.CronParser).parse('* * * * * * *') to switch to basic mode"