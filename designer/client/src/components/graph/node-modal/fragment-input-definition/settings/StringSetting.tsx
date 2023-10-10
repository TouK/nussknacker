import React, { useState } from "react";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import PresetTypesSetting from "./PresetTypesSetting";
import { variables } from "../../../../../stylesheets/variables";
import { FormControlLabel, Radio, RadioGroup } from "@mui/material";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { isValidOption } from "../item/utils";

interface StringSetting {
    onChange: (path: string, value: onChangeType) => void;
    item: UpdatedItem;
    currentOption: Option;
    path: string;
}

export default function StringSetting({ onChange, path, item, currentOption }: StringSetting) {
    const { t } = useTranslation();
    const [localInputMode] = useState(["Fixed list", "Any value with suggestions", "Any value"]);

    return (
        <>
            {isValidOption(currentOption) && (
                <>
                    <SettingRow>
                        <SettingLabelStyled>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                        <TypeSelect
                            onChange={(value) => onChange(`${path}.inputMode`, value)}
                            value={{ value: item.inputMode ?? localInputMode[0], label: item.inputMode ?? localInputMode[0] }}
                            options={localInputMode.map((option) => ({ value: option, label: option }))}
                        />
                    </SettingRow>
                    <SettingRow>
                        <SettingLabelStyled></SettingLabelStyled>
                        <RadioGroup
                            aria-labelledby="demo-controlled-radio-buttons-group"
                            name="controlled-radio-buttons-group"
                            value={item.presetType}
                            onChange={(event) => {
                                onChange(`${path}.presetType`, event.target.value);
                                if (event.target.value !== "Preset") {
                                    onChange(`${path}.addListItem`, []);
                                } else {
                                    onChange(`${path}.presetSelection`, []);
                                }
                            }}
                        >
                            <FormControlLabel
                                sx={{ color: variables.defaultTextColor }}
                                value="Preset"
                                control={<Radio />}
                                label={t("fragment.settings.preset", "Preset")}
                            />
                            <FormControlLabel
                                sx={{ color: variables.defaultTextColor }}
                                value="UserDefinitionList"
                                control={<Radio />}
                                label={t("fragment.settings.userDefinedList", "User defined list")}
                            />
                        </RadioGroup>
                    </SettingRow>
                    <PresetTypesSetting
                        path={path}
                        onChange={onChange}
                        presetType={item.presetType}
                        presetSelection={item.presetSelection}
                        addListItem={item.addListItem}
                    />
                </>
            )}
        </>
    );
}
