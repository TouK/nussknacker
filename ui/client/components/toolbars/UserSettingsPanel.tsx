import {defaultsDeep} from "lodash"
import React from "react"
import {useTranslation} from "react-i18next"
import Creatable from "react-select/creatable"
import {useUserSettings} from "../../common/userSettings"
import {useNkTheme} from "../../containers/theme"
import {CollapsibleToolbar} from "../toolbarComponents/CollapsibleToolbar"

export function UserSettingsPanel(): JSX.Element {
  const {t} = useTranslation()
  const {theme} = useNkTheme()
  const [settings, , reset] = useUserSettings()
  const value = Object.entries(settings).map(([label, value]) => ({label, value}))
  return (
    <CollapsibleToolbar id="user-settings-panel" title={t("panels.userSettings.title", "🧪 User settings")}>
      <Creatable
        isMulti
        value={value}
        getOptionValue={option => `${option.label}_${option.value}`}
        onChange={values => reset(values?.reduce((current, {label, value}) => ({...current, [label]: !!value}), {}))}
        isValidNewOption={inputValue => /^[^_]/.test(inputValue)}
        theme={provided => defaultsDeep(theme, provided)}
        styles={{
          multiValue: base => ({...base, width: "100%"}),
          multiValueLabel: base => ({...base, width: "100%", fontWeight: "bold"}),
          control: base => ({...base, padding: 0}),
          valueContainer: base => ({...base, padding: 4, flexWrap: "wrap-reverse"}),
        }}
        components={{
          DropdownIndicator: null,
          ClearIndicator: null,
          Menu,
          MultiValueLabel,
        }}
      />
    </CollapsibleToolbar>
  )

}

const Menu = () => <></>

const MultiValueLabel = ({data, innerProps}) => {
  const [, toggle] = useUserSettings()
  return (
    <span onClick={() => toggle([data.label])} className={innerProps.className}>
      {data.value ? "✅" : "⛔️"} {data.label}
    </span>
  )
}
