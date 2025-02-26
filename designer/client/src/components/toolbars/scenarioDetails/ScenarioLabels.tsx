import { useDispatch, useSelector } from "react-redux";
import { getScenarioLabels, getScenarioLabelsErrors } from "../../../reducers/selectors/graph";
import {
    Autocomplete,
    AutocompleteInputChangeReason,
    Box,
    Chip,
    CircularProgress,
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
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { editScenarioLabels } from "../../../actions/nk";
import { debounce } from "lodash";
import { ScenarioLabelValidationError } from "../../Labels/types";
import { useTranslation } from "react-i18next";
import { useDelayedEnterAction } from "./useDelayedEnterAction";

interface AddLabelProps {
    onClick: () => void;
}

const labelUniqueValidation = (label: string) => ({
    label,
    messages: [
        i18next.t("panels.scenarioDetails.labels.validation.uniqueValue", "This label already exists. Please enter a unique value."),
    ],
});

const AddLabel = ({ onClick }: AddLabelProps) => {
    return (
        <Typography
            data-testid="AddLabel"
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

const StyledLabelChip = styled(Chip)({
    borderRadius: "16px",
    margin: "2px",
});

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
    const { t } = useTranslation();
    const autocompleteRef = useRef(null);
    const scenarioLabels = useSelector(getScenarioLabels);
    const scenarioLabelOptions: LabelOption[] = useMemo(() => scenarioLabels.map(toLabelOption), [scenarioLabels]);
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

    const [inputTyping, setInputTyping] = useState(false);
    const [inputErrors, setInputErrors] = useState<ScenarioLabelValidationError[]>([]);

    const handleAddLabelClick = (): void => {
        setShowEditor(true);
        setIsEdited(true);
    };

    const isInputInSelectedOptions = useCallback(
        (inputValue: string): boolean => {
            return scenarioLabelOptions.some((option) => inputValue === toLabelValue(option));
        },
        [scenarioLabelOptions],
    );

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

            if (isInputInSelectedOptions(newInput)) {
                setInputErrors((prevState) => [...prevState, labelUniqueValidation(newInput)]);
            }

            setInputTyping(false);
        }, 500);
    }, [isInputInSelectedOptions]);

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

    const setLabels = useCallback(
        (options: LabelOption[]) => {
            const newLabels = options.map(toLabelValue);
            dispatch(editScenarioLabels(newLabels));
        },
        [dispatch],
    );

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

    const { setIsEnterPressed } = useDelayedEnterAction({
        action: () => {
            const enterEvent = new KeyboardEvent("keydown", {
                key: "Enter",
                keyCode: 13,
                code: "Enter",
                bubbles: true,
                cancelable: true,
            });
            autocompleteRef.current.dispatchEvent(enterEvent);
        },
        errorsLength: inputErrors.length,
        inputTyping,
    });

    return (
        <>
            {!showEditor ? (
                <AddLabel onClick={handleAddLabelClick} />
            ) : (
                <StyledAutocomplete
                    ref={autocompleteRef}
                    data-testid={"Labels"}
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
                    isOptionEqualToValue={(v1: LabelOption, v2: LabelOption) => v1.value === v2.value}
                    loading={isFetching || inputTyping}
                    clearOnBlur
                    loadingText={<CircularProgress size={"1rem"} />}
                    multiple
                    noOptionsText={i18next.t("panels.scenarioDetails.labels.noAvailableLabels", "No labels")}
                    onBlur={() => {
                        setIsEdited(false);
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
                            setIsEnterPressed(false);
                            setInputTyping(true);
                        }
                        setInputErrors([]);
                        validateInput(newInputValue);
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
                                data-testid={"LabelInput"}
                                size="small"
                                {...params}
                                variant="outlined"
                                autoFocus={isEdited}
                                error={labelOptionsErrors.length !== 0 || inputErrors.length !== 0}
                                helperText={inputHelperText}
                                inputProps={{
                                    ...params.inputProps,
                                    onKeyDown: (event) => {
                                        const input = (event.target as HTMLInputElement).value;

                                        if (
                                            event.key === "Enter" &&
                                            (inputErrors.length !== 0 || inputTyping || isInputInSelectedOptions(input))
                                        ) {
                                            setIsEnterPressed(true);
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
                                    <StyledLabelChip
                                        title={t("panels.scenarioDetails.tooltip.label", "Label")}
                                        key={key}
                                        data-testid={`scenario-label-${index}`}
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
