import React, { useState } from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../../components/withFocus";
import { useTranslation } from "react-i18next";
import { PresetType, UpdatedItem, onChangeType } from "../item";
import { ListItems } from "./ListItems";
import { TypeSelect } from "../TypeSelect";

interface PresetTypesSetting extends Pick<UpdatedItem, "presetSelection" | "fixedValueList" | "presetSelection" | "fixedValueList"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    presetType: PresetType;
}

export default function PresetTypesSetting({ fixedValueList, presetSelection, path, presetType, onChange }: PresetTypesSetting) {
    const { t } = useTranslation();
    const [temporareListItem, setTemporeryListItem] = useState("");

    return (
        <>
            {presetType === "Preset" ? (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        onChange={(value) => onChange(`${path}.presetSelection`, value)}
                        value={{ value: presetSelection, label: presetSelection }}
                        options={[{ value: "option", label: "option" }]}
                    />
                </SettingRow>
            ) : (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
                    <NodeInput
                        style={{ width: "70%" }}
                        value={temporareListItem}
                        onChange={(e) => setTemporeryListItem(e.currentTarget.value)}
                        onKeyUp={(event) => {
                            if (event.key === "Enter") {
                                const updatedList = [...fixedValueList, temporareListItem];
                                onChange(`${path}.fixedValueList`, updatedList);
                                setTemporeryListItem("");
                            }
                        }}
                    />
                    {fixedValueList?.length > 0 && <ListItems fixedValueList={fixedValueList} onChange={onChange} path={path} />}
                </SettingRow>
            )}
        </>
    );
}
