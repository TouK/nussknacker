import React, { useState } from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../../../withFocus";
import { useTranslation } from "react-i18next";
import { PresetType, UpdatedItem, onChangeType } from "../../../item";
import { ListItems } from "./ListItems";
import { Option, TypeSelect } from "../../../TypeSelect";

interface PresetTypesSetting extends Pick<UpdatedItem, "presetSelection" | "fixedValueList"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    presetType: PresetType;
    presetListOptions: Option[];
}

export default function PresetTypesSetting({ fixedValueList, path, presetType, onChange, presetListOptions }: PresetTypesSetting) {
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");

    return (
        <>
            {presetType === "Preset" ? (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        onChange={(value) => onChange(`${path}.presetSelection`, value)}
                        value={{ value: "Defined list 1", label: "Defined list 1" }}
                        options={presetListOptions}
                    />
                </SettingRow>
            ) : (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
                    <NodeInput
                        style={{ width: "70%" }}
                        value={temporaryListItem}
                        onChange={(e) => setTemporaryListItem(e.currentTarget.value)}
                        onKeyUp={(event) => {
                            if (event.key === "Enter") {
                                const updatedList = [...fixedValueList, temporaryListItem];
                                onChange(`${path}.fixedValueList`, updatedList);
                                setTemporaryListItem("");
                            }
                        }}
                    />
                    {fixedValueList?.length > 0 && <ListItems fixedValueList={fixedValueList} onChange={onChange} path={path} />}
                </SettingRow>
            )}
        </>
    );
}
