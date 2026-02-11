import type { AutocompleteInputChangeReason, SxProps, Theme } from "@mui/material";
import {
    Autocomplete,
    Box,
    Chip,
    CircularProgress,
    createFilterOptions,
    Link,
    styled,
    TextField,
    Typography,
    useTheme,
} from "@mui/material";
import i18next from "i18next";
import { debounce } from "lodash";
import React, { createRef, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { editScenarioLabels } from "../../../actions/nk/editNode";
import HttpService from "../../../http/HttpService/instance";
import { getScenarioLabels } from "../../../reducers/selectors/graph";
import { getScenarioLabelsErrors } from "../../../reducers/selectors/graph2";
import { useAppDispatch, useAppSelector } from "../../../store/storeHelpers";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import type { ScenarioLabelValidationError } from "../../Labels/types";
import { useDelayedEnterAction } from "./useDelayedEnterAction";

interface AddLabelProps {
    onClick: () => void;
}

const isValidWidthsArray = (widths: number[]): boolean => widths.every((w) => w >= 0 && w !== undefined);

const findThresholdIndex = (numbers: number[], threshold: number): number => {
    let sum = 0;
    for (let i = 0; i < numbers.length; i++) {
        sum += numbers[i];
        if (sum > threshold) return i;
    }
    return -1;
};

const labelUniqueValidation = (label: string) => ({
    label,
    messages: [
        i18next.t("panels.scenarioDetails.labels.validation.uniqueValue", "This label already exists. Please enter a unique value."),
    ],
});

const AddLabel = ({ onClick, disabled }: AddLabelProps & { disabled?: boolean }) => {
    return (
        <Typography
            data-testid="AddLabel"
            component={Link}
            variant={"caption"}
            sx={(theme) => ({
                cursor: disabled ? "default" : "pointer",
                textDecoration: "none",
                color: disabled ? theme.palette.text.disabled : theme.palette.text.primary,
                "&:hover": {
                    textDecoration: disabled ? "none" : "underline",
                },
            })}
            onClick={!disabled ? onClick : undefined}
        >
            {i18next.t("panels.scenarioDetails.labels.addLabelTitle", "+ Add label")}
        </Typography>
    );
};

const StyledAutocomplete = styled(Autocomplete, {
    shouldForwardProp: (propName: string) => !["isEdited"].includes(propName),
})<{ isEdited: boolean }>(({ isEdited, theme }) => ({
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
    const autocompleteRef = useRef<HTMLInputElement | null>(null);
    const scenarioLabels = useAppSelector(getScenarioLabels);
    const scenarioLabelOptions: LabelOption[] = useMemo(() => scenarioLabels.map(toLabelOption), [scenarioLabels]);
    const initialScenarioLabelOptionsErrors = useAppSelector(getScenarioLabelsErrors).filter((error) =>
        scenarioLabelOptions.some((option) => toLabelValue(option) === error.label),
    );
    const [labelOptionsErrors, setLabelOptionsErrors] = useState<ScenarioLabelValidationError[]>(initialScenarioLabelOptionsErrors);
    const [showEditor, setShowEditor] = useState(scenarioLabelOptions.length !== 0);

    const theme = useTheme();
    const { menuOption } = selectStyled(theme);
    const dispatch = useAppDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [options, setOptions] = useState<LabelOption[]>([]);

    const [inputTyping, setInputTyping] = useState(false);
    const [inputErrors, setInputErrors] = useState<ScenarioLabelValidationError[]>([]);

    const labelsRefs: React.RefObject<HTMLDivElement>[] = Array.from({ length: scenarioLabels.length }, () => createRef<HTMLDivElement>());
    const [labelsInputWidth, setLabelsInputWidth] = useState<number>(200);
    const [dynamicTagsLimit, setDynamicTagsLimit] = useState(999);

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
        const widths = labelsRefs.map((ref) =>
            !autocompleteRef.current.classList.contains("Mui-focused") ? ref.current?.offsetWidth : -1,
        );

        if (isValidWidthsArray(widths)) {
            setDynamicTagsLimit(findThresholdIndex(widths, labelsInputWidth));
        }
    }, [labelsRefs, labelsInputWidth]);

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
                <AddLabel onClick={handleAddLabelClick} disabled={readOnly} />
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
                    limitTags={dynamicTagsLimit}
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
                        setLabelsInputWidth(autocompleteRef.current.offsetWidth * 0.8);
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
                                    sx: {
                                        display: params.inputProps.disabled ? "none" : "block", // Hides the input when edition is disabled
                                    },
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
                                        ref={labelsRefs[index]}
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
