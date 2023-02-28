import React, { PropsWithChildren, useCallback } from "react";
import { FiltersContextProvider } from "../common";
import { useScenariosWithStatus } from "./useScenariosQuery";
import { ScenariosFiltersModel } from "./filters/scenariosFiltersModel";
import { FiltersPart } from "./filters";
import { Add } from "@mui/icons-material";
import { Avatar, Button, SpeedDial, SpeedDialAction, SpeedDialIcon, Stack, styled } from "@mui/material";
import { useInViewRef } from "rooks";
import { ValueLinker } from "../common/filters/filtersContext";
import { TablePart } from "./list/tablePart";
import { ListPart } from "./list/listPart";

const StyledSpeedDial = styled(SpeedDial)(({ theme }) => ({
    position: "fixed",
    bottom: theme.spacing(2),
    right: theme.spacing(2),
    [theme.breakpoints.down("xl")]: {
        bottom: theme.spacing(1.5),
        right: theme.spacing(1.5),
    },
    [theme.breakpoints.down("md")]: {
        bottom: theme.spacing(2),
        right: theme.spacing(4),
    },
}));

function Actions({ addScenario, addFragment }: { addScenario?: () => void; addFragment?: () => void }): JSX.Element {
    const [ref, inView] = useInViewRef();

    if (!addScenario && !addFragment) {
        return null;
    }

    return (
        <>
            <Stack ref={ref} direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                {addScenario && (
                    <Button size="small" variant="contained" disableElevation startIcon={<Add />} onClick={addScenario}>
                        New scenario
                    </Button>
                )}
                {addFragment && (
                    <Button size="small" variant="contained" disableElevation startIcon={<Add />} onClick={addFragment}>
                        New fragment
                    </Button>
                )}
            </Stack>
            <StyledSpeedDial ariaLabel="Create new..." icon={<SpeedDialIcon />} TransitionProps={{ in: !inView }}>
                {addFragment && (
                    <SpeedDialAction
                        tooltipTitle={"New fragment"}
                        onClick={addFragment}
                        icon={<Avatar sx={{ bgcolor: "grey.800" }}>F</Avatar>}
                    />
                )}
                {addScenario && (
                    <SpeedDialAction
                        tooltipTitle={"New scenario"}
                        onClick={addScenario}
                        icon={<Avatar sx={{ bgcolor: "grey.800" }}>S</Avatar>}
                    />
                )}
                x
            </StyledSpeedDial>
        </>
    );
}

function ScenariosView({ children, table }: PropsWithChildren<{ table?: boolean }>): JSX.Element {
    const { data = [], isLoading, isFetching } = useScenariosWithStatus();
    const valueLinker: ValueLinker<ScenariosFiltersModel> = useCallback(
        (setNewValue) => (id, value) => {
            switch (id) {
                case "HIDE_SCENARIOS":
                    return value && setNewValue("HIDE_FRAGMENTS", false);
                case "HIDE_FRAGMENTS":
                    return value && setNewValue("HIDE_SCENARIOS", false);
            }
        },
        [],
    );
    return (
        <FiltersContextProvider<ScenariosFiltersModel> getValueLinker={valueLinker}>
            {children}
            <FiltersPart data={data} isLoading={isFetching} withSort={!table} />
            {!table ? <TablePart data={data} isLoading={isLoading} /> : <ListPart data={data} isLoading={isLoading} />}
        </FiltersContextProvider>
    );
}

export const ScenariosWithActions = (props: { addScenario?: () => void; addFragment?: () => void }) => (
    <ScenariosView table={false}>
        <Actions {...props} />
    </ScenariosView>
);
