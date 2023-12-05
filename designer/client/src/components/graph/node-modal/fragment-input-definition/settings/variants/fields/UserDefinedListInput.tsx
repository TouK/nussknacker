import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { EditableEditor } from "../../../../editors/EditableEditor";
import { ExpressionLang } from "../../../../editors/expression/types";
import AceEditor from "react-ace";
import { ListItems } from "./ListItems";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { FieldName, FixedValuesOption, onChangeType } from "../../../item";
import { ReturnedType, VariableTypes } from "../../../../../../../types";
import { Error, uniqueValueValidator, validators } from "../../../../editors/Validators";
import HttpService from "../../../../../../../http/HttpService";
import { useSelector } from "react-redux";
import { getProcessToDisplay } from "../../../../../../../reducers/selectors/graph";
import { GenericValidationRequest } from "../../../../../../../actions/nk/genericAction";
import { debounce } from "lodash";
import { EditorType } from "../../../../editors/expression/Editor";
import { useSettings } from "../../SettingsProvider";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesList: FixedValuesOption[];
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: Error[];
    typ: ReturnedType;
    name: string;
    initialValue: FixedValuesOption;
}

export const UserDefinedListInput = ({
    fixedValuesList,
    path,
    onChange,
    variableTypes,
    readOnly,
    errors,
    typ,
    name,
    initialValue,
}: Props) => {
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");
    const [temporaryValuesTyping, setTemporaryValuesTyping] = useState(false);
    const [temporaryValueErrors, setTemporaryValueErrors] = useState<Error[]>([]);

    const { handleTemporaryUserDefinedList } = useSettings();

    const userDefinedListOptions = (fixedValuesList ?? []).map(({ label }) => ({ label, value: label }));

    const handleDeleteDefinedListItem = (currentIndex: number) => {
        const filteredItemsList = fixedValuesList.filter((_, index) => index !== currentIndex);
        if (filteredItemsList) {
            handleChangeFixedValuesList(filteredItemsList);

            const initialValueOnTheList = filteredItemsList.find((filteredItemsList) => filteredItemsList.label !== initialValue.label);

            if (!initialValueOnTheList) {
                onChange(`${path}.initialValue`, null);
            }

            const isUniqueValueValidator = uniqueValueValidator(filteredItemsList.map((filteredItemList) => filteredItemList.label));
            if (isUniqueValueValidator.isValid(temporaryListItem)) {
                const removeUniqueValidationError = temporaryValueErrors.filter(
                    (temporaryValueError) => temporaryValueError.message !== isUniqueValueValidator.message(),
                );

                setTemporaryValueErrors(removeUniqueValidationError);
            }
        }
    };

    const temporaryListItemTyp = useMemo(
        () => ({
            type: "TypedClass",
            display: "",
            refClazzName: typ.refClazzName,
            params: [],
        }),
        [typ.refClazzName],
    );

    const handleChangeFixedValuesList = (fixedValuesList: FixedValuesOption[]) => {
        onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
        handleTemporaryUserDefinedList(fixedValuesList);
    };

    const handleAddNewListItem = () => {
        const isUniqueValueValidator = uniqueValueValidator(fixedValuesList.map((fixedValuesList) => fixedValuesList.label));
        const mandatoryParameterValidator = validators.MandatoryParameterValidator();

        if (!mandatoryParameterValidator.isValid(temporaryListItem)) {
            setTemporaryValueErrors((prevState) => [
                ...prevState,
                {
                    fieldName: temporaryItemName,
                    typ: temporaryListItemTyp.refClazzName,
                    description: mandatoryParameterValidator.description(),
                    message: mandatoryParameterValidator.message(),
                },
            ]);
            return;
        }

        if (!isUniqueValueValidator.isValid(temporaryListItem)) {
            setTemporaryValueErrors((prevState) => [
                ...prevState,
                {
                    fieldName: temporaryItemName,
                    typ: temporaryListItemTyp.refClazzName,
                    description: isUniqueValueValidator.description(),
                    message: isUniqueValueValidator.message(),
                },
            ]);
            return;
        }

        if (temporaryValueErrors.length === 0 && !temporaryValuesTyping) {
            const updatedList = [...fixedValuesList, { expression: temporaryListItem, label: temporaryListItem }];
            handleChangeFixedValuesList(updatedList);
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

    const { processingType } = useSelector(getProcessToDisplay);
    const temporaryItemName: FieldName = `$param.${name}.$fixedValuesListTemporaryItem`;

    const validateTemporaryListItem = useMemo(() => {
        return debounce(async (expressionVariable: string) => {
            const genericValidationRequest: GenericValidationRequest = {
                parameters: [
                    {
                        name: temporaryItemName,
                        typ: temporaryListItemTyp,
                        expression: { language: ExpressionLang.SpEL, expression: expressionVariable },
                    },
                ],
                variableTypes: {},
            };

            const response = await HttpService.validateGenericActionParameters(processingType, genericValidationRequest);

            if (response.status === 200) {
                setTemporaryValueErrors(response.data.validationErrors);
            }

            setTemporaryValuesTyping(false);
        }, 500);
    }, [processingType, temporaryItemName, temporaryListItemTyp]);

    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.addListItem", "Add list item:")}</SettingLabelStyled>
            <EditableEditor
                validationLabelInfo={temporaryValuesTyping && "Typing..."}
                fieldName={temporaryItemName}
                expressionObj={{ language: ExpressionLang.SpEL, expression: temporaryListItem }}
                onValueChange={(value) => {
                    setTemporaryListItem(value);
                    setTemporaryValuesTyping(true);
                    setTemporaryValueErrors([]);
                    validateTemporaryListItem(value);
                }}
                variableTypes={variableTypes}
                readOnly={readOnly}
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
                <ListItems
                    items={fixedValuesList}
                    handleDelete={readOnly ? undefined : handleDeleteDefinedListItem}
                    errors={errors}
                    fieldName={`$param.${name}.$fixedValuesList`}
                />
            )}
        </SettingRow>
    );
};
