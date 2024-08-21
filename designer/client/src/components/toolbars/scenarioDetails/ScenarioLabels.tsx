import { useDispatch, useSelector } from "react-redux";
import { getScenario, getScenarioLabels } from "../../../reducers/selectors/graph";
import { Autocomplete, Box, Link, SxProps, TextField, Theme, Typography, useTheme } from "@mui/material";
import { selectStyled } from "../../../stylesheets/SelectStyled";
import React, { ForwardRefExoticComponent, useCallback, useEffect, useMemo, useState } from "react";
import HttpService from "../../../http/HttpService";
import i18next from "i18next";
import { Chip } from "@mui/material";
import { editScenarioLabels } from "../../../actions/nk";

interface Props {
    readOnly: boolean;
}

export type LabelsEdit = React.ComponentType<Props> | ForwardRefExoticComponent<Props>;

const AddLabel = ({ onClick }) => {
    return (
        <Typography
            component={Link}
            variant={"caption"}
            sx={(theme) => ({ cursor: "pointer", textDecoration: "none" })}
            onClick={(e) => onClick()}
        >
            {i18next.t("panels.scenarioDetails.addScenarioLabel", "+ Add label")}
        </Typography>
    );
};

export const ScenarioLabels: LabelsEdit = ({ readOnly }: Props) => {
    const scenario = useSelector(getScenario);
    const scenarioLabels = useSelector(getScenarioLabels);
    const theme = useTheme();
    const { menuOption, labelsInput } = selectStyled(theme);
    const dispatch = useDispatch();

    const [isFetching, setIsFetching] = useState(false);
    const [isOpen, setIsOpen] = useState(false);
    const [isEdited, setIsEdited] = useState(false);
    const [showLabelsEditor, setShowLabelsEditor] = useState(scenarioLabels.length != 0);
    const [options, setOptions] = useState<string[]>([]);
    const handleAddLabelClick = () => {
        setShowLabelsEditor(true);
    };

    // todo debounce or refresh to limit calls
    const fetchLabels = useCallback(async () => {
        setIsFetching(true);
        const { data } = await HttpService.fetchScenarioLabels();
        setIsFetching(false);
        return data.labels;
    }, [scenario.name]);

    const saveLabels = (labels: string[]) => {
        const sortedLabels = labels.sort((a, b) => a.localeCompare(b));
        dispatch(editScenarioLabels(sortedLabels));
    };

    return (
        <div>
            {!showLabelsEditor ? (
                <AddLabel onClick={handleAddLabelClick} />
            ) : (
                <Autocomplete
                    id="scenario-labels"
                    clearOnBlur={true}
                    disabled={readOnly}
                    disableClearable={!isEdited}
                    disableCloseOnSelect={false}
                    freeSolo
                    filterSelectedOptions={true}
                    loading={isFetching}
                    multiple
                    noOptionsText={i18next.t("panels.scenarioDetails.noAvailableScenarioLabels", "No labels")}
                    onBlur={() => {
                        setIsEdited(false);
                        if (scenarioLabels.length == 0) {
                            setShowLabelsEditor(false);
                        }
                    }}
                    onChange={(_, labels) => {
                        saveLabels(labels);
                    }}
                    onClose={() => {
                        setIsOpen(false);
                    }}
                    onFocus={() => {
                        setIsEdited(true);
                    }}
                    onOpen={async () => {
                        const fetchedAvailableLabels = await fetchLabels();
                        setOptions(fetchedAvailableLabels);
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
                                sx={labelsInput({}, !isEdited) as SxProps<Theme>}
                                // we need to set the props below to disable input's border
                                // sx={{"& fieldset": { border: 'none' }} }
                            />
                        </div>
                    )}
                    renderOption={(props, option) => {
                        return (
                            <Box component={"li"} sx={menuOption({}, false, false) as SxProps<Theme>} {...props} aria-selected={false}>
                                {option}
                            </Box>
                        );
                    }}
                    renderTags={(value: readonly string[], getTagProps) => {
                        return value.map((option: string, index: number) => {
                            const { key, ...tagProps } = getTagProps({ index });
                            const props = isEdited ? { ...tagProps } : {};
                            return <Chip key={key} size="small" variant="outlined" disabled={readOnly} label={option} {...props} />;
                        });
                    }}
                    size="small"
                    value={scenarioLabels}
                />
            )}
        </div>
    );
};
