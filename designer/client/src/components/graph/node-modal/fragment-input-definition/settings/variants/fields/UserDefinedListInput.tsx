import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { EditableEditor } from "../../../../editors/EditableEditor";
import { ExpressionLang } from "../../../../editors/expression/types";
import AceEditor from "react-ace";
import { ListItems } from "./ListItems";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { FixedValuesOption, onChangeType } from "../../../item";
import { VariableTypes } from "../../../../../../../types";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesList: FixedValuesOption[];
    variableTypes: VariableTypes;
    readOnly: boolean;
}

export const UserDefinedListInput = ({ fixedValuesList, path, onChange, variableTypes, readOnly }: Props) => {
    const [temporaryListItem, setTemporaryListItem] = useState("");
    const { t } = useTranslation();

    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const handleDeleteDefinedListItem = (currentIndex: number) => {
        const filteredItemList = fixedValuesList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.inputConfig.fixedValuesList`, filteredItemList);
    };

    const handleAddNewListItem = () => {
        const isUniqueValue = fixedValuesList.every((fixedValuesItem) => fixedValuesItem.label.trim() !== temporaryListItem.trim());
        const isEmptyValue = !temporaryListItem;

        if (isUniqueValue && !isEmptyValue) {
            const updatedList = [...fixedValuesList, { expression: temporaryListItem, label: temporaryListItem }];
            onChange(`${path}.inputConfig.fixedValuesList`, updatedList);
            setTemporaryListItem("");
        }
    };

    const ENTER_VALUE_COMMAND = "addValueOnEnter";
    const aceEditorEnterCommand = {
        name: ENTER_VALUE_COMMAND,
        bindKey: { win: "enter", mac: "enter" },
        exec: () => {
            handleAddNewListItem();
            return true;
        },
    };
    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
            <EditableEditor
                fieldName="addListItem"
                expressionObj={{ language: ExpressionLang.SpEL, expression: temporaryListItem }}
                onValueChange={(value) => setTemporaryListItem(value)}
                variableTypes={variableTypes}
                readOnly={readOnly}
                data-testid={"add-list-item"}
                ref={(ref: AceEditor | null) => {
                    if (ref?.editor) {
                        ref.editor.commands.addCommand(aceEditorEnterCommand);
                    }
                }}
            />
            {userDefinedListOptions?.length > 0 && (
                <ListItems items={fixedValuesList} handleDelete={readOnly ? undefined : handleDeleteDefinedListItem} />
            )}
        </SettingRow>
    );
};
