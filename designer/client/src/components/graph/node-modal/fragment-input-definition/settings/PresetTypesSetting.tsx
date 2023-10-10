import React, { useState } from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../../components/withFocus";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import { ListItems } from "./ListItems";
import { TypeSelect } from "../TypeSelect";

interface PresetTypesSetting extends Pick<UpdatedItem, "presetSelection" | "presetType" | "presetSelection" | "addListItem"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export default function PresetTypesSetting({ presetType, presetSelection, path, addListItem, onChange }: PresetTypesSetting) {
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
                                const updatedList = [...addListItem, temporareListItem];
                                onChange(`${path}.addListItem`, updatedList);
                                setTemporeryListItem("");
                            }
                        }}
                    />
                    {addListItem.length > 0 && <ListItems addListItem={addListItem} onChange={onChange} path={path} />}
                </SettingRow>
            )}
        </>
    );
}
