import { useDispatch, useSelector } from "react-redux";
import { getScenarioLabels, getScenarioLabelsErrors } from "../../../reducers/selectors/graph";
import {
    Autocomplete,
    AutocompleteInputChangeReason,
    Box,
    Chip,
    createFilterOptions,
    Link,
    styled,
    SxProps,
    TextField,
    Theme,
    Typography,
    useTheme,
} from "@mui/material";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import React, { ForwardRefExoticComponent, useCallback, useEffect, useMemo, useState } from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { editScenarioLabels } from "../../../actions/nk";
import { debounce } from "lodash";
import { ScenarioLabelValidationError } from "../../Labels/types";

interface AddLabelProps {
    onClick: () => void;
}

const AddLabel = ({ onClick }: AddLabelProps) => {
    return (
        <Typography
            component={Link}
            variant={"caption"}
            sx={(theme) => ({ cursor: "pointer", textDecoration: "none", color: theme.palette.text.primary })}
            onClick={onClick}
        >
            {i18next.t("panels.scenarioDetails.labels.addLabelTitle", "+ Add label")}
        </Typography>
    );
};

const StyledAutocomplete = styled(Autocomplete)<{ isEdited: boolean }>(({ isEdited, theme }) => ({
    ".MuiFormControl-root": {
        margin: 0,
        flexDirection: "column",
    },
    ...{
        ...(!isEdited && {
            "&:hover": {
                backgroundColor: theme.palette.action.hover,
            },
            ".MuiInputBase-input": {
                outline: "none",
            },
            ".MuiOutlinedInput-notchedOutline": {
                border: "none",
            },
        }),
    },
}));

const filter = createFilterOptions<string | LabelOption>();

type LabelOption = {
    title: string;
    value: string;
    inputValue?: string;
};

function toLabelOption(value: string): LabelOption {
    return {
        title: value,
        value: value,
    };
}

function toLabelValue(option: LabelOption): string {
    return option.value;
}

function formatErrors(errors: ScenarioLabelValidationError[]): string {
    return errors
        .map(
            (error) =>
                `${i18next.t("panels.scenarioDetails.labels.incorrectLabel", "Incorrect value")} '${error.label}': ${error.messages.join(
                    ",",
                )}`,
        )
        .join("\n");
}

interface Props {
    readOnly: boolean;
}

export const ScenarioLabels = ({ readOnly }: Props) => {
    const scenarioLabelOptions: LabelOption[] = useSelector(getScenarioLabels).map(toLabelOption);
    const initialScenarioLabelOptionsErrors = useSelector(getScenarioLabelsErrors).filter((error) =>
        scenarioLabelOptions.some((option) => toLabelValue(option) === error.label),
    );
    const [labelOptionsErrors, setLabelOptionsErrors] = useState<ScenarioLabelValidationError[]>(initialScenarioLabelOptionsErrors);
    const [showEditor, setShowEditor] = useState(scenarioLabelOptions.length !== 0);

    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const dispatch = useDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [options, setOptions] = useState<LabelOption[]>([]);

    const [input, setInput] = useState("");
    const [inputTyping, setInputTyping] = useState(false);
    const [inputErrors, setInputErrors] = useState<ScenarioLabelValidationError[]>([]);

    const handleAddLabelClick = (): void => {
        setShowEditor(true);
        setIsEdited(true);
    };

    const isInputInSelectedOptions = (inputValue: string): boolean => {
        return scenarioLabelOptions.some((option) => inputValue === toLabelValue(option));
    };

    const inputHelperText = useMemo(() => {
        if (inputErrors.length !== 0) {
            return formatErrors(inputErrors.concat(labelOptionsErrors));
        }

        if (!isOpen && labelOptionsErrors.length !== 0) {
            return formatErrors(labelOptionsErrors);
        }

        return undefined;
    }, [inputErrors, labelOptionsErrors, isOpen]);

    const validateInput = useMemo(() => {
        return debounce(async (newInput: string) => {
            if (newInput !== "") {
                const response = await HttpService.validateScenarioLabels([newInput]);

                if (response.status === 200) {
                    setInputErrors(response.data.validationErrors);
                }
            }

            setInputTyping(false);
        }, 500);
    }, []);

    const validateSelectedOptions = useMemo(() => {
        return debounce(async (labels: LabelOption[]) => {
            const values = labels.map(toLabelValue);
            const response = await HttpService.validateScenarioLabels(values);

            if (response.status === 200) {
                const validationError = response.data.validationErrors;
                setLabelOptionsErrors(validationError);
            }
        }, 500);
    }, []);

    const fetchAvailableLabelOptions = useCallback(async () => {
        try {
            setIsFetching(true);
            const { data } = await HttpService.fetchScenarioLabels();
            return data.labels.map(toLabelOption);
        } finally {
            setIsFetching(false);
        }
    }, []);

    const setLabels = (options: LabelOption[]) => {
        const newLabels = options.map(toLabelValue);
        dispatch(editScenarioLabels(newLabels));
    };

    useEffect(() => {
        validateSelectedOptions(scenarioLabelOptions);
    }, [scenarioLabelOptions, validateSelectedOptions]);

    useEffect(() => {
        // show add label component if user clears all labels and looses focus
        if (!isEdited && scenarioLabelOptions.length === 0) {
            setShowEditor(false);
        }
    }, [scenarioLabelOptions, isEdited]);

    useEffect(() => {
        // show editor if user use edit options (undo, redo) and editor is hidden
        if (!showEditor && scenarioLabelOptions.length !== 0) {
            setShowEditor(true);
        }
    }, [scenarioLabelOptions, showEditor]);

    return (
        <>
            {!showEditor ? (
                <AddLabel onClick={handleAddLabelClick} />
            ) : (
                <StyledAutocomplete
                    isEdited={isEdited}
                    id="scenario-labels"
                    disabled={readOnly}
                    disableClearable={false}
                    disableCloseOnSelect={false}
                    freeSolo
                    fullWidth
                    filterOptions={(options: (string | LabelOption)[], params) => {
                        const filtered = filter(options, params);

                        const { inputValue } = params;

                        const isInProposedOptions = filtered.some((option) =>
                            typeof option === "string" ? false : inputValue === toLabelValue(option),
                        );
                        const isInSelectedOptions = isInputInSelectedOptions(inputValue);

                        if (inputValue !== "" && !isInProposedOptions && !isInSelectedOptions && !inputTyping && inputErrors.length === 0) {
                            filtered.push({
                                inputValue,
                                title: `${i18next.t("panels.scenarioDetails.labels.addNewLabel", "Add label")} "${inputValue}"`,
                                value: inputValue,
                            });
                        }

                        return inputErrors.length !== 0 ? [] : filtered;
                    }}
                    filterSelectedOptions={true}
                    getOptionLabel={(option: string | LabelOption) => {
                        if (typeof option === "string") {
                            return option;
                        }
                        return option.title;
                    }}
                    getOptionKey={(option: string | LabelOption) => {
                        if (typeof option === "string") {
                            return option;
                        }
                        return toLabelValue(option);
                    }}
                    inputValue={input}
                    isOptionEqualToValue={(v1: LabelOption, v2: LabelOption) => v1.value === v2.value}
                    loading={isFetching || inputTyping}
                    loadingText={
                        inputTyping
                            ? i18next.t("panels.scenarioDetails.labels.labelTyping", "Typing...")
                            : i18next.t("panels.scenarioDetails.labels.labelsLoading", "Loading...")
                    }
                    multiple
                    noOptionsText={i18next.t("panels.scenarioDetails.labels.noAvailableLabels", "No labels")}
                    onBlur={() => {
                        setIsEdited(false);
                        setInput("");
                        setInputErrors([]);
                        if (scenarioLabelOptions.length === 0) {
                            setShowEditor(false);
                        }
                    }}
                    onChange={(_, values: (string | LabelOption)[]) => {
                        const labelOptions = values.map((value) => {
                            if (typeof value === "string") {
                                return toLabelOption(value);
                            } else if (value.inputValue) {
                                return toLabelOption(value.inputValue);
                            } else {
                                return value;
                            }
                        });
                        setLabels(labelOptions);
                    }}
                    onClose={() => {
                        setIsOpen(false);
                    }}
                    onFocus={() => {
                        setIsEdited(true);
                    }}
                    onInputChange={(_, newInputValue: string, reason: AutocompleteInputChangeReason) => {
                        if (reason === "input") {
                            setInputTyping(true);
                        }
                        setInputErrors([]);
                        validateInput(newInputValue);
                        setInput(newInputValue);
                    }}
                    onOpen={async () => {
                        const fetchedOptions = await fetchAvailableLabelOptions();
                        setOptions(fetchedOptions);
                        setIsOpen(true);
                    }}
                    open={isOpen}
                    options={options}
                    renderInput={(params) => (
                        <div ref={params.InputProps.ref}>
                            <TextField
                                size="small"
                                {...params}
                                variant="outlined"
                                autoFocus={isEdited}
                                error={labelOptionsErrors.length !== 0 || inputErrors.length !== 0}
                                helperText={inputHelperText}
                                inputProps={{
                                    ...params.inputProps,
                                    onKeyDown: (event) => {
                                        if (
                                            event.key === "Enter" &&
                                            (inputErrors.length !== 0 || inputTyping || isInputInSelectedOptions(input))
                                        ) {
                                            event.stopPropagation();
                                        }
                                    },
                                }}
                            />
                        </div>
                    )}
                    renderOption={(props, option, _, ownerState) => {
                        return (
                            <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
                                {ownerState.getOptionLabel(option)}
                            </Box>
                        );
                    }}
                    renderTags={(value: (string | LabelOption)[], getTagProps) => {
                        return value
                            .filter((v) => typeof v !== "string")
                            .map((option: LabelOption, index: number) => {
                                const { key, ...tagProps } = getTagProps({ index });
                                const props = isEdited ? { ...tagProps } : {};
                                const labelError = labelOptionsErrors.find((error) => error.label === toLabelValue(option));
                                return (
                                    <Chip
                                        key={key}
                                        color={labelError ? "error" : "default"}
                                        size="small"
                                        variant="outlined"
                                        disabled={readOnly}
                                        label={option.title}
                                        {...props}
                                    />
                                );
                            });
                    }}
                    size="small"
                    value={scenarioLabelOptions}
                />
            )}
        </>
    );
};
