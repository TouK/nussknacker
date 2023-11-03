import React, { useState } from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../../../withFocus";
import { useTranslation } from "react-i18next";
import { PresetType, UpdatedItem, onChangeType, FixedValuesOption, FixedValuesPresetOption } from "../../../item";
import { ListItems } from "./ListItems";
import { Option, TypeSelect } from "../../../TypeSelect";

interface PresetTypesSetting extends Pick<UpdatedItem, "presetSelection"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    presetType: PresetType;
    fixedValuesList: FixedValuesOption[];
    fixedValuesPresets: FixedValuesPresetOption;
    fixedValuesListPresetId: string;
}

export default function PresetTypesSetting({
    path,
    presetType,
    onChange,
    fixedValuesListPresetId,
    fixedValuesPresets,
    fixedValuesList,
}: PresetTypesSetting) {
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");

    const presetListOptions: Option[] = Object.keys(fixedValuesPresets).map((key) => ({ label: key, value: key }));
    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    return (
        <>
            {presetType === "Preset" ? (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        onChange={(value) => {
                            onChange(`${path}.fixedValuesListPresetId`, value);
                            onChange(`${path}.initialValue`, "");
                        }}
                        value={presetListOptions.find((presetListOption) => presetListOption.value === fixedValuesListPresetId)}
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
                                const updatedList = [...fixedValuesList, { expression: temporaryListItem, label: temporaryListItem }];
                                onChange(`${path}.fixedValuesList`, updatedList);
                                setTemporaryListItem("");
                            }
                        }}
                    />
                    {userDefinedListOptions?.length > 0 && <ListItems fixedValuesList={fixedValuesList} onChange={onChange} path={path} />}
                </SettingRow>
            )}
        </>
    );
}
