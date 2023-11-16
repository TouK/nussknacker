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
    readOnly: boolean;
}

export function FixedValuesSetting({
    path,
    fixedValuesType = FixedValuesType.UserDefinedList,
    onChange,
    fixedValuesListPresetId,
    fixedValuesPresets,
    fixedValuesList,
    readOnly,
}: FixedValuesSetting) {
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");

    const presetListOptions: Option[] = Object.keys(fixedValuesPresets ?? {}).map((key) => ({ label: key, value: key }));
    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const selectedPresetValueExpressions: Option[] = (fixedValuesPresets?.[fixedValuesListPresetId] ?? []).map(
        (selectedPresetValueExpression) => ({ label: selectedPresetValueExpression.label, value: selectedPresetValueExpression.label }),
    );

    const handleDeleteDefinedListItem = (currentIndex: number) => {
        const filteredItemList = fixedValuesList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.inputConfig.fixedValuesList`, filteredItemList);
    };
    return (
        <>
            {fixedValuesType === FixedValuesType.Preset && (
                <SettingRow>
                    <SettingLabelStyled required>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
                    <TypeSelect
                        readOnly={readOnly}
                        onChange={(value) => {
                            onChange(`${path}.fixedValuesListPresetId`, value);
                            onChange(`${path}.initialValue`, null);
                        }}
                        value={presetListOptions.find((presetListOption) => presetListOption.value === fixedValuesListPresetId)}
                        options={presetListOptions}
                    />
                    {selectedPresetValueExpressions?.length > 0 && <ListItems items={selectedPresetValueExpressions} />}
                </SettingRow>
            )}
            {fixedValuesType === FixedValuesType.UserDefinedList && (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
                    <NodeInput
                        style={{ width: "70%" }}
                        value={temporaryListItem}
                        onChange={(e) => setTemporaryListItem(e.currentTarget.value)}
                        onKeyUp={(event) => {
                            const isUniqueValue = fixedValuesList.every(
                                (fixedValuesItem) => fixedValuesItem.label.trim() !== temporaryListItem.trim(),
                            );
                            const isEmptyValue = !temporaryListItem;

                            if (event.key === "Enter" && isUniqueValue && !isEmptyValue) {
                                const updatedList = [...fixedValuesList, { expression: temporaryListItem, label: temporaryListItem }];
                                onChange(`${path}.inputConfig.fixedValuesList`, updatedList);
                                setTemporaryListItem("");
                            }
                        }}
                        readOnly={readOnly}
                        data-testid={"add-list-item"}
                    />
                    {userDefinedListOptions?.length > 0 && (
                        <ListItems items={fixedValuesList} handleDelete={readOnly ? undefined : handleDeleteDefinedListItem} />
                    )}
                </SettingRow>
            )}
        </>
    );
}
