import React, { PropsWithChildren } from "react";
import { FiltersContextProvider } from "../common";
import { useScenariosWithStatus } from "./useScenariosQuery";
import { ScenariosFiltersModel } from "./filters/scenariosFiltersModel";
import { FiltersPart } from "./filters";
import { ListPart } from "./list/listPart";
import { Add } from "@mui/icons-material";
import { Avatar, Button, SpeedDial, SpeedDialAction, SpeedDialIcon, Stack, styled } from "@mui/material";
import { useInViewRef } from "rooks";

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

function ScenariosView({ children }: PropsWithChildren<unknown>): JSX.Element {
    const { data = [], isLoading, isFetching } = useScenariosWithStatus();
    return (
        <FiltersContextProvider<ScenariosFiltersModel>
            getValueLinker={(setNewValue) => (id, value) => {
                switch (id) {
                    case "HIDE_SCENARIOS":
                        return value && setNewValue("HIDE_FRAGMENTS", false);
                    case "HIDE_FRAGMENTS":
                        return value && setNewValue("HIDE_SCENARIOS", false);
                    case "HIDE_ACTIVE":
                        return value && setNewValue("SHOW_ARCHIVED", true);
                    case "SHOW_ARCHIVED":
                        return !value && setNewValue("HIDE_ACTIVE", false);
                    case "HIDE_DEPLOYED":
                        return value && setNewValue("HIDE_NOT_DEPLOYED", false);
                    case "HIDE_NOT_DEPLOYED":
                        return value && setNewValue("HIDE_DEPLOYED", false);
                }
            }}
        >
            {children}
            <FiltersPart data={data} isLoading={isFetching} />
            <ListPart data={data} isLoading={isLoading} />
        </FiltersContextProvider>
    );
}

export const ScenariosWithActions = (props: { addScenario?: () => void; addFragment?: () => void }) => (
    <ScenariosView>
        <Actions {...props} />
    </ScenariosView>
);
