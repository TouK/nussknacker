import React, { useState } from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../../../withFocus";
import { useTranslation } from "react-i18next";
import { FixedValuesType, onChangeType, FixedValuesOption, FixedListParameterVariant } from "../../../item";
import { ListItems } from "./ListItems";
import { Option, TypeSelect } from "../../../TypeSelect";
import { FixedValuesPresets } from "../../../../../../../types";

interface FixedValuesSetting extends Pick<FixedListParameterVariant, "presetSelection"> {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesType: FixedValuesType;
    fixedValuesList: FixedValuesOption[];
    fixedValuesPresets: FixedValuesPresets;
    fixedValuesListPresetId: string;
}

export function FixedValuesSetting({
    path,
    fixedValuesType,
    onChange,
    fixedValuesListPresetId,
    fixedValuesPresets,
    fixedValuesList,
}: FixedValuesSetting) {
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");

    const presetListOptions: Option[] = Object.keys(fixedValuesPresets).map((key) => ({ label: key, value: key }));
    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const selectedPresetValueExpressions: Option[] = (fixedValuesPresets?.[fixedValuesListPresetId] ?? []).map(
        (selectedPresetValueExpression) => ({ label: selectedPresetValueExpression.label, value: selectedPresetValueExpression.label }),
    );

    const handleDeleteDefinedListItem = (currentIndex: number) => {
        const filteredItemList = fixedValuesList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.fixedValuesList`, filteredItemList);
    };
    return (
        <>
            {fixedValuesType === "Preset" ? (
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
                    {selectedPresetValueExpressions?.length > 0 && <ListItems items={selectedPresetValueExpressions} />}
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
                    {userDefinedListOptions?.length > 0 && <ListItems items={fixedValuesList} handleDelete={handleDeleteDefinedListItem} />}
                </SettingRow>
            )}
        </>
    );
}
