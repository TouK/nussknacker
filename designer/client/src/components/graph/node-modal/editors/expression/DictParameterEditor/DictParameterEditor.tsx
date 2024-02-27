import React, { useCallback, useMemo, useState } from "react";
import { useAutocomplete } from "@mui/material";
import HttpService, { ProcessDefinitionDataDictOption } from "../../../../../../http/HttpService";
import { getScenario } from "../../../../../../reducers/selectors/graph";
import { useSelector } from "react-redux";
import { debounce } from "@mui/material/utils";
import { SimpleEditor } from "../Editor";
import { ExpressionObj } from "../types";
import { FieldError } from "../../Validators";
import { ParamType } from "../../types";

interface Props {
    expressionObj: ExpressionObj;
    onValueChange: (value: string) => void;
    fieldErrors: FieldError[];
    param: ParamType;
}

export const DictParameterEditor: SimpleEditor<Props> = (props: Props) => {
    const scenario = useSelector(getScenario);

    const [options, setOptions] = useState<ProcessDefinitionDataDictOption[]>([]);
    const [value, setValue] = React.useState(() => {
        if (!props.expressionObj.expression) {
            return null;
        }

        return JSON.parse(JSON.stringify(props.expressionObj.expression));
    });
    const [inputValue, setInputValue] = React.useState("");

    const fetchProcessDefinitionDataDict = useCallback(
        async (inputValue: string) => {
            const response = await HttpService.fetchProcessDefinitionDataDict(
                scenario.processingType,
                props.param.editor.dictId,
                inputValue,
            );

            return response.data;
        },
        [props.param.editor.dictId, scenario.processingType],
    );

    const debouncedUpdateOptions = useMemo(() => {
        return debounce(async (value: string) => {
            const fetchedOptions = await fetchProcessDefinitionDataDict(value);
            setOptions(fetchedOptions);
        }, 400);
    }, [fetchProcessDefinitionDataDict]);

    const { groupedOptions, getInputProps, getOptionProps, getListboxProps } = useAutocomplete({
        options,
        filterOptions: (x) => x,
        onOpen: async () => {
            const fetchedOptions = await fetchProcessDefinitionDataDict(inputValue);
            setOptions(fetchedOptions);
        },
        isOptionEqualToValue: () => true,
        getOptionLabel: (option) => option.label,
        onChange: (_, value) => {
            props.onValueChange(value?.key || "");
            setValue(value);
        },
        value,
        inputValue,
        onInputChange: async (event, value) => {
            await debouncedUpdateOptions(value);
            setInputValue(value);
        },
    });

    return (
        <div>
            <input {...getInputProps()} />
            <ul {...getListboxProps()}>
                {groupedOptions.map((option, index) => (
                    <li key={index} {...getOptionProps({ option, index })}>
                        {option.label}
                    </li>
                ))}
            </ul>
        </div>
    );
};
