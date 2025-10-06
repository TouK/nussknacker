import { Button, Typography } from "@mui/material";
import { capitalize, startCase } from "lodash";
import type { ProcessStateType } from "nussknackerUi/components/Process/types";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { FiltersContextType } from "../../common/filters/filtersContext";
import { NuIcon } from "../../common/nuIcon";
import type { ScenariosFiltersModel } from "../filters/scenariosFiltersModel";

interface Props {
    state: ProcessStateType;
    filtersContext: FiltersContextType<ScenariosFiltersModel>;
}

export const ScenarioStatus = ({ state, filtersContext }: Props) => {
    const { t } = useTranslation();
    const { setFilter, getFilter } = filtersContext;
    const filterValue = useMemo(() => getFilter("STATUS", true), [getFilter]);
    const value = state.status.name;

    const isSelected = useMemo(() => filterValue.includes(value), [filterValue, value]);

    const onClick = useCallback(
        (e) => {
            setFilter("STATUS")(isSelected ? filterValue.filter((v) => v !== value) : [...filterValue, value]);
            e.preventDefault();
            e.stopPropagation();
        },
        [setFilter, isSelected, filterValue, value],
    );

    return (
        <Button
            title={t("scenariosList.tooltip.status", "Status")}
            color={isSelected ? "primary" : "inherit"}
            sx={{ textTransform: "capitalize", display: "flex", gap: 1, alignItems: "center", fontSize: "1rem", py: 0.25, px: 0.25 }}
            onClick={onClick}
        >
            <NuIcon
                title={t("scenario.iconTitle", "{{tooltip}}", {
                    context: value,
                    tooltip: state.tooltip,
                })}
                src={state.icon}
            />
            <Typography variant={"caption"} noWrap>
                {capitalize(startCase(value))}
            </Typography>
        </Button>
    );
};
