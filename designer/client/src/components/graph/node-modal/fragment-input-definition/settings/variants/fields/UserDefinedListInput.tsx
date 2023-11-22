import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { EditableEditor } from "../../../../editors/EditableEditor";
import { ExpressionLang } from "../../../../editors/expression/types";
import AceEditor from "react-ace";
import { ListItems } from "./ListItems";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FixedValuesOption, onChangeType } from "../../../item";
import { ReturnedType, VariableTypes } from "../../../../../../../types";
import { Error } from "../../../../editors/Validators";
import HttpService from "../../../../../../../http/HttpService";
import { useSelector } from "react-redux";
import { getProcessProperties } from "../../../../NodeDetailsContent/selectors";
import { getProcessId, getProcessToDisplay } from "../../../../../../../reducers/selectors/graph";
import { GenericValidationRequest } from "../../../../../../../actions/nk/genericAction";
import { debounce } from "lodash";
import { EditorType } from "../../../../editors/expression/Editor";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesList: FixedValuesOption[];
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: Error[];
    typ: ReturnedType;
}

export const UserDefinedListInput = ({ fixedValuesList, path, onChange, variableTypes, readOnly, errors, typ }: Props) => {
    const [temporaryListItem, setTemporaryListItem] = useState("");
    const { t } = useTranslation();
    const [temporaryValuesChecking, setTemporaryValuesChecking] = useState(true);

    const [temporaryValueErrors, setTemporaryValueErrors] = useState<Error[]>([]);

    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const handleDeleteDefinedListItem = (currentIndex: number) => {
        const filteredItemList = fixedValuesList.filter((_, index) => index !== currentIndex);
        onChange(`${path}.inputConfig.fixedValuesList`, filteredItemList);
    };

    const handleAddNewListItem = () => {
        const isUniqueValue = fixedValuesList.every((fixedValuesItem) => fixedValuesItem.label.trim() !== temporaryListItem.trim());
        const isEmptyValue = !temporaryListItem;

        if (isUniqueValue && !isEmptyValue && temporaryValueErrors.length === 0 && !temporaryValuesChecking) {
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

    const processProperties = useSelector(getProcessProperties);
    const scenarioName = useSelector(getProcessId);
    const { processingType } = useSelector(getProcessToDisplay);

    const validateTemporaryListItem = useMemo(() => {
        return debounce(async (test: string) => {
            const genericValidationRequest: GenericValidationRequest = {
                parameters: [
                    {
                        name: "fixedValuesList",
                        typ: {
                            type: "TypedClass",
                            display: "",
                            refClazzName: typ.refClazzName,
                            params: [],
                        },
                        expression: { language: ExpressionLang.SpEL, expression: test },
                    },
                ],
                processProperties,
                variableTypes: {},
                scenarioName,
            };

            const response = await HttpService.validateGenericActionParameters(processingType, genericValidationRequest);

            if (response.status === 200) {
                setTemporaryValueErrors(response.data.validationErrors);
            }

            setTemporaryValuesChecking(false);
        }, 500);
    }, [processProperties, processingType, scenarioName, typ.refClazzName]);

    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
            <EditableEditor
                fieldName="fixedValuesList"
                expressionObj={{ language: ExpressionLang.SpEL, expression: temporaryListItem }}
                onValueChange={(value) => {
                    setTemporaryListItem(value);
                    setTemporaryValuesChecking(true);
                    validateTemporaryListItem(value);
                }}
                variableTypes={variableTypes}
                readOnly={readOnly}
                data-testid={"add-list-item"}
                ref={(ref: AceEditor | null) => {
                    if (ref?.editor) {
                        ref.editor.commands.addCommand(aceEditorEnterCommand);
                    }
                }}
                param={{ validators: [], editor: { type: EditorType.RAW_PARAMETER_EDITOR } }}
                errors={temporaryValueErrors}
                showValidation
            />
            {userDefinedListOptions?.length > 0 && (
                <ListItems items={fixedValuesList} handleDelete={readOnly ? undefined : handleDeleteDefinedListItem} errors={errors} />
            )}
        </SettingRow>
    );
};
