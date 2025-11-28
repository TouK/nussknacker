import { cx } from "@emotion/css";
import type { SxProps, Theme } from "@mui/material";
import { Autocomplete, Box, useTheme } from "@mui/material";
import { debounce } from "@mui/material/utils";
import i18next from "i18next";
import { isEmpty } from "lodash";
import React, { useCallback, useEffect, useMemo, useState } from "react";

import { tryParseOrNull } from "../../../../../../common/JsonUtils";
import HttpService from "../../../../../../http/HttpService/instance";
import type { ProcessDefinitionDataDictOption } from "../../../../../../http/HttpService/types";
import { getScenario } from "../../../../../../reducers/selectors/graph";
import { useAppSelector } from "../../../../../../store/storeHelpers";
import { selectStyled } from "../../../../../../stylesheets/SelectStyled";
import { NodeInput } from "../../../../../FormElements";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import { nodeInput, nodeInputWithError, nodeValue } from "../../../NodeDetailsContent/NodeTableStyled";
import type { FieldError } from "../../Validators";
import type { OnValueChange } from "../Editor";
import { prepareEditor } from "../Editor";
import type { EditorConfigForType } from "../EditorConfig";
import { editorsParameters } from "../editorsParameters";
import type { ExpressionObj } from "../types";
import { EditorType, ExpressionLang } from "../types";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: OnValueChange;
    fieldErrors: FieldError[];
    editorConfig: EditorConfigForType<EditorType.DICT_PARAMETER_EDITOR>;
    showValidation: boolean;
    readOnly: boolean;
}

const isParseable = (expressionObj: ExpressionObj) =>
    tryParseOrNull(expressionObj.expression) && typeof tryParseOrNull(expressionObj.expression) === "object";

export const DictParameterEditor = prepareEditor<Props>(
    ({ fieldErrors, expressionObj, editorConfig, onValueChange, showValidation, readOnly }) => {
        const scenario = useAppSelector(getScenario);
        const theme = useTheme();
        const { menuOption } = selectStyled(theme);
        const [options, setOptions] = useState<ProcessDefinitionDataDictOption[]>([]);
        const [open, setOpen] = useState(false);
        const [value, setValue] = useState<ProcessDefinitionDataDictOption>();
        const [inputValue, setInputValue] = useState("");
        const [isFetching, setIsFetching] = useState(false);
        const [isLoading, setIsLoading] = useState(true);

        const dictId = editorConfig?.dictId;

        const fetchProcessDefinitionDataDict = useCallback(
            async (inputValue: string) => {
                setIsFetching(true);
                const { data } = await HttpService.fetchProcessDefinitionDataDict(scenario.processingType, dictId, inputValue);

                setIsFetching(false);

                return data;
            },
            [dictId, scenario.processingType],
        );

        const fetchProcessDefinitionDataDictByKey = useCallback(
            async (key: string) => {
                setIsFetching(true);
                const response = await HttpService.fetchProcessDefinitionDataDictByKey(scenario.processingType, dictId, key);
                setIsFetching(false);
                return response;
            },
            [dictId, scenario.processingType],
        );

        const debouncedUpdateOptions = useMemo(() => {
            return debounce(async (value: string) => {
                const fetchedOptions = await fetchProcessDefinitionDataDict(value);
                setOptions(fetchedOptions);
            }, 400);
        }, [fetchProcessDefinitionDataDict]);

        const isValid = isEmpty(fieldErrors);

        // This logic is needed, because scenario is initially loaded without full validation data.
        // In that case the label is missing, and we need to fetch it separately.
        useEffect(() => {
            if (!expressionObj.expression) return;
            const parseObject = tryParseOrNull(expressionObj.expression);
            if (!parseObject) {
                setIsLoading(false);
                return;
            }
            fetchProcessDefinitionDataDictByKey(parseObject?.key)
                .then((response) => {
                    if (response.status == "success") {
                        setValue(response.data);
                    } else {
                        setValue(parseObject);
                    }
                })
                .finally(() => {
                    setIsLoading(false);
                });
        }, [expressionObj, fetchProcessDefinitionDataDictByKey]);

        // This condition means, that we should delay rendering this fragment when both conditions are met:
        // - expression is defined, so we know that value is present, but we do not yet have enough information to render it (label)
        // - value is not yet available - label is not yet loaded
        if (isLoading && expressionObj?.expression) {
            return;
        }

        return (
            <Box className={nodeValue}>
                <Autocomplete
                    disabled={readOnly}
                    renderInput={({ inputProps, InputProps }) => (
                        <div ref={InputProps.ref}>
                            <NodeInput
                                {...inputProps}
                                className={cx(
                                    nodeInput,
                                    inputProps.className,
                                    showValidation && !isValid && nodeInputWithError,
                                    readOnly && "read-only",
                                )}
                            />
                        </div>
                    )}
                    options={options}
                    filterOptions={(x) => x}
                    onChange={(_, value) => {
                        onValueChange({
                            expression: value ? JSON.stringify(value) : "",
                            language: editorsParameters[EditorType.DICT_PARAMETER_EDITOR].language,
                        });
                        setValue(value);
                        setOpen(false);
                    }}
                    onOpen={async () => {
                        // On open we show all the options
                        const fetchedOptions = await fetchProcessDefinitionDataDict("");
                        setOptions(fetchedOptions);
                        setOpen(true);
                    }}
                    onClose={() => {
                        setOpen(false);
                    }}
                    open={open}
                    noOptionsText={i18next.t("editors.dictParameterEditor.noOptionsFound", "No options found")}
                    getOptionLabel={(option) => option.label ?? ""}
                    isOptionEqualToValue={() => true}
                    value={value}
                    inputValue={inputValue}
                    loading={isFetching}
                    renderOption={(props, option) => {
                        const isSelected = option.key === value?.key;
                        return (
                            // aria-selected is set to false as it overrides styles defined in our menuOption
                            <Box component={"li"} sx={menuOption({}, isSelected, false) as SxProps<Theme>} {...props} aria-selected={false}>
                                {option.label}
                            </Box>
                        );
                    }}
                    onInputChange={async (event, value) => {
                        await debouncedUpdateOptions(value);
                        setInputValue(value);
                    }}
                />
                {showValidation && <ValidationLabels fieldErrors={fieldErrors} />}
            </Box>
        );
    },
    {
        notSwitchableToHint: () => i18next.t("editors.dictParameter.notSwitchableToHint", ""),
        isSwitchableTo: () => true,
        parseValueOnEditorChange: (expressionObj: ExpressionObj, newLanguage: ExpressionLang) => {
            if (expressionObj.language === ExpressionLang.SpEL) {
                return {
                    language: newLanguage,
                    expression: isParseable(expressionObj) ? expressionObj.expression : "",
                };
            }

            return expressionObj;
        },
    },
);
