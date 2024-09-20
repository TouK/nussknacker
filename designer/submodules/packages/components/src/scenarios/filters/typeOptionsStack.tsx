import { useFilterContext } from "../../common";
import React from "react";
import { useTranslation } from "react-i18next";
import { ScenariosFiltersModel, ScenariosFiltersModelType } from "./scenariosFiltersModel";
import { FilterListItem, FilterListItemSwitch } from "./filterListItem";
import { OptionsStack } from "./optionsStack";
import { Divider, Stack } from "@mui/material";
import { xor } from "lodash";
import { FilterListItemLabel } from "./filterListItemLabel";
import ScanarioIcon from "../../assets/icons/scenario.svg";
import FragmentIcon from "../../assets/icons/fragment.svg";
import { EventTrackingSelector, getEventTrackingProps } from "nussknackerUi/eventTracking";

export function TypeOptionsStack(): JSX.Element {
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const otherFilters: Array<keyof ScenariosFiltersModel> = ["TYPE"];
    const getTypeFilter = () => getFilter("TYPE", true);

    return (
        <OptionsStack
            label={t("table.filter.TYPE", "Type")}
            options={otherFilters.map((name) => ({ name }))}
            value={otherFilters
                .flatMap((k) => getFilter(k))
                .filter(Boolean)
                .map(toString)}
            onChange={(v) => otherFilters.forEach((k) => setFilter(k, v))}
        >
            <FilterListItem
                checkboxProps={{ color: "default" }}
                checked={getFilter("TYPE", true)?.includes(ScenariosFiltersModelType.SCENARIOS)}
                onChange={() => setFilter("TYPE", xor([ScenariosFiltersModelType.SCENARIOS], getTypeFilter()))}
                label={
                    <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                        <span>{t("table.filter.SCENARIOS", "Scenarios")}</span>
                        <ScanarioIcon width={"1em"} height={"1em"} />
                    </Stack>
                }
                {...getEventTrackingProps({ selector: EventTrackingSelector.ScenariosByOther })}
            />
            <FilterListItem
                checkboxProps={{ color: "default" }}
                checked={getFilter("TYPE", true)?.includes(ScenariosFiltersModelType.FRAGMENTS)}
                onChange={() => setFilter("TYPE", xor([ScenariosFiltersModelType.FRAGMENTS], getTypeFilter()))}
                label={
                    <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                        <span>{t("table.filter.FRAGMENTS", "Fragments")}</span>
                        <FragmentIcon width={"1em"} height={"1em"} />
                    </Stack>
                }
                {...getEventTrackingProps({ selector: EventTrackingSelector.ScenariosByOther })}
            />
        </OptionsStack>
    );
}

export interface StatusFilterOption {
    name: string;
    displayableName: string;
    icon: string;
}

interface StatusFiltersParams {
    options?: StatusFilterOption[];
    withArchived?: boolean;
}

export function StatusOptionsStack(props: StatusFiltersParams): JSX.Element {
    const { options = [], withArchived, ...passProps } = props;
    const { t } = useTranslation();
    const { getFilter, setFilter } = useFilterContext<ScenariosFiltersModel>();
    const filters: Array<keyof ScenariosFiltersModel> = ["ARCHIVED", "STATUS"];

    const value = getFilter("STATUS", true);
    const onChange = setFilter("STATUS");

    return (
        <OptionsStack
            label={t("table.filter.STATUS", "Status")}
            options={filters.map((name) => ({ name }))}
            value={filters
                .flatMap((k: keyof ScenariosFiltersModel) => getFilter(k))
                .filter(Boolean)
                .map(toString)}
            onChange={(v) => filters.forEach((k: keyof ScenariosFiltersModel) => setFilter(k, v))}
        >
            {options.map((option) => {
                const isSelected = value.includes(option.name);
                const onClick = (checked: boolean) => onChange(checked ? [...value, option.name] : value.filter((v) => v !== option.name));
                return (
                    <FilterListItem
                        key={option.name}
                        checked={isSelected}
                        onChange={onClick}
                        label={<FilterListItemLabel {...option} />}
                        {...passProps}
                    />
                );
            })}
            {withArchived ? (
                <>
                    <Divider />
                    <FilterListItemSwitch
                        checked={getFilter("ARCHIVED") === true}
                        onChange={(checked) => setFilter("ARCHIVED", checked)}
                        label={t("table.filter.ARCHIVED", "Archived")}
                    />
                </>
            ) : null}
        </OptionsStack>
    );
}
