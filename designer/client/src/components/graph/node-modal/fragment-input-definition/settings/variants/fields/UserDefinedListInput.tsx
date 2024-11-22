import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { EditableEditor } from "../../../../editors/EditableEditor";
import { ExpressionLang } from "../../../../editors/expression/types";
import AceEditor from "react-ace";
import { ListItems } from "./ListItems";
import React, { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FieldName, FixedValuesOption, onChangeType } from "../../../item";
import { NodeValidationError, ReturnedType, VariableTypes } from "../../../../../../../types";
import { getValidationErrorsForField, mandatoryValueValidator, uniqueValueValidator } from "../../../../editors/Validators";
import HttpService from "../../../../../../../http/HttpService";
import { useSelector } from "react-redux";
import { getProcessingType } from "../../../../../../../reducers/selectors/graph";
import { GenericValidationRequest } from "../../../../../../../actions/nk/adhocTesting";
import { debounce } from "lodash";
import { EditorType } from "../../../../editors/expression/Editor";
import { useSettings } from "../../SettingsProvider";
import { Box, Button, CircularProgress, FormControl, Stack } from "@mui/material";
import { useDelayedEnterAction } from "../../../../../../toolbars/scenarioDetails/useDelayedEnterAction";
import { IAceEditor } from "react-ace/lib/types";

const ENTER_VALUE_COMMAND = "addValueOnEnter";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    fixedValuesList: FixedValuesOption[];
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
    typ: ReturnedType;
    name: string;
    initialValue: FixedValuesOption;
    inputLabel: string;
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
    inputLabel,
}: Props) => {
    const editorRef = useRef<IAceEditor>(null);
    const { t } = useTranslation();
    const [temporaryListItem, setTemporaryListItem] = useState("");
    const [temporaryValuesTyping, setTemporaryValuesTyping] = useState(false);
    const [temporaryValueErrors, setTemporaryValueErrors] = useState<NodeValidationError[]>([]);

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

    const { setIsEnterPressed } = useDelayedEnterAction({
        action: () => {
            editorRef.current.execCommand(ENTER_VALUE_COMMAND);
        },
        errorsLength: temporaryValueErrors.length,
        inputTyping: temporaryValuesTyping,
    });

    const handleChangeFixedValuesList = (fixedValuesList: FixedValuesOption[]) => {
        onChange(`${path}.valueEditor.fixedValuesList`, fixedValuesList);
        handleTemporaryUserDefinedList(fixedValuesList);
    };

    const handleAddNewListItem = () => {
        if (temporaryValuesTyping) {
            setIsEnterPressed(true);
            return;
        }

        const isUniqueValueValidator = uniqueValueValidator(fixedValuesList.map((fixedValuesList) => fixedValuesList.label));

        if (!mandatoryValueValidator.isValid(temporaryListItem)) {
            setTemporaryValueErrors((prevState) => [
                ...prevState,
                {
                    errorType: "SaveAllowed",
                    fieldName: temporaryItemName,
                    typ: temporaryListItemTyp.refClazzName,
                    description: mandatoryValueValidator.description(),
                    message: mandatoryValueValidator.message(),
                },
            ]);
            return;
        }

        if (!isUniqueValueValidator.isValid(temporaryListItem)) {
            setTemporaryValueErrors((prevState) => [
                ...prevState,
                {
                    errorType: "SaveAllowed",
                    fieldName: temporaryItemName,
                    typ: temporaryListItemTyp.refClazzName,
                    description: isUniqueValueValidator.description(),
                    message: isUniqueValueValidator.message(),
                },
            ]);
            return;
        }

        if (temporaryValueErrors.length === 0) {
            const updatedList = [...fixedValuesList, { expression: temporaryListItem, label: temporaryListItem }];
            handleChangeFixedValuesList(updatedList);
            setTemporaryListItem("");
        }
    };

    const aceEditorEnterCommand = {
        name: ENTER_VALUE_COMMAND,
        bindKey: { win: "enter", mac: "enter" },
        exec: () => {
            handleAddNewListItem();
            return true;
        },
    };

    const processingType = useSelector(getProcessingType);
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
        <FormControl>
            <SettingLabelStyled>{inputLabel}</SettingLabelStyled>
            <Box width={"80%"} flex={1}>
                <Stack direction="row" paddingY={1} spacing={1} justifyContent={"space-between"} alignItems={"start"}>
                    <EditableEditor
                        validationLabelInfo={
                            temporaryValuesTyping && <CircularProgress size={"1rem"} sx={(theme) => ({ marginTop: theme.spacing(0.5) })} />
                        }
                        expressionObj={{ language: ExpressionLang.SpEL, expression: temporaryListItem }}
                        onValueChange={(value) => {
                            setTemporaryListItem(value);
                            setTemporaryValuesTyping(true);
                            setTemporaryValueErrors([]);
                            validateTemporaryListItem(value);
                            setIsEnterPressed(false);
                        }}
                        variableTypes={variableTypes}
                        readOnly={readOnly}
                        ref={(ref: AceEditor | null) => {
                            if (ref?.editor) {
                                editorRef.current = ref.editor;
                                ref.editor.commands.addCommand(aceEditorEnterCommand);
                            }
                        }}
                        param={{ editor: { type: EditorType.RAW_PARAMETER_EDITOR } }}
                        fieldErrors={getValidationErrorsForField(temporaryValueErrors, temporaryItemName)}
                        showValidation
                    />
                    <Button variant="contained" onClick={handleAddNewListItem}>
                        {t("fragment.addListItemButton", "Add")}
                    </Button>
                </Stack>
                {userDefinedListOptions?.length > 0 && (
                    <ListItems
                        items={fixedValuesList}
                        handleDelete={readOnly ? undefined : handleDeleteDefinedListItem}
                        errors={errors}
                        fieldName={`$param.${name}.$fixedValuesList`}
                    />
                )}
            </Box>
        </FormControl>
    );
};
