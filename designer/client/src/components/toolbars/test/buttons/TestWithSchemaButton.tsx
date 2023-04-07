import React, {useEffect, useState} from "react"
import {useTranslation} from "react-i18next"
import {useSelector} from "react-redux"
import {ReactComponent as Icon} from "../../../../assets/img/toolbarButtons/test-with-schema.svg"
import {
  getTestCapabilities, getTestViewParameters,
  isLatestProcessVersion
} from "../../../../reducers/selectors/graph"
import {useWindows, WindowKind} from "../../../../windowManager"
import {ToolbarButtonProps} from "../../types"
import ToolbarButton from "../../../toolbarComponents/ToolbarButton";
import _ from "lodash"

type Props = ToolbarButtonProps

function TestWithSchemaButton(props: Props) {
  const {disabled} = props
  const {t} = useTranslation()
  const processIsLatestVersion = useSelector(isLatestProcessVersion)
  const testCapabilities = useSelector(getTestCapabilities)
  const testViewParameters = useSelector(getTestViewParameters)
  const available = !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canCreateTestView
  const {open} = useWindows()


  const [action, setAction] = useState(null)

  useEffect(() => {
    const params = _.head(testViewParameters?.map((viewParameters) => viewParameters.parameters))
    setAction({
      layout: {
        name: "Test",
        confirmText: "Test"
      },
      allowedStateStatusNames: [],
      parameters: params,
      parametersValues: (params || []).reduce((obj, param) => ({
        ...obj,
        [param.name]: param.defaultValue.expression,
      }), {}),
      onParamUpdate
    });
  }, [testViewParameters]);

  const onParamUpdate = (name: string) => (value: any) => setAction(current => (
    {
      ...current,
      parametersValues: {...current.parametersValues, [name]: value},
    })
  )

  return (
    <ToolbarButton
      name={t("panels.actions.test-with-schema.button", "test window")}
      hasError={false} //TODO
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
