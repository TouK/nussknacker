import React, { PropsWithChildren } from "react";
import { FiltersContextProvider } from "../common";
import { useScenariosWithStatus } from "./useScenariosQuery";
import { ScenariosFiltersModel } from "./filters/scenariosFiltersModel";
import { FiltersPart } from "./filters";
import { Avatar, Button, SpeedDial, SpeedDialAction, SpeedDialIcon, Stack, styled } from "@mui/material";
import { useInViewRef } from "rooks";
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

export interface ActionsProps {
    addScenario?: () => void;
    addFragment?: () => void;
}

function Actions({ addScenario, addFragment }: ActionsProps): JSX.Element {
    const [ref, inView] = useInViewRef();

    if (!addScenario && !addFragment) {
        return null;
    }

    return (
        <>
            <Stack ref={ref} direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                {addScenario && (
                    <Button variant="contained" size={"small"} onClick={addScenario}>
                        Add New scenario
                    </Button>
                )}
                {addFragment && (
                    <Button variant="contained" size={"small"} onClick={addFragment}>
                        Add New fragment
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

function Scenarios({ children, table }: PropsWithChildren<{ table?: boolean }>): JSX.Element {
    const { data = [], isLoading, isFetching } = useScenariosWithStatus();
    return (
        <FiltersContextProvider<ScenariosFiltersModel>>
            {children}
            <FiltersPart data={data} isLoading={isFetching} withSort={!table} />
            {table ? <TablePart data={data} isLoading={isLoading} /> : <ListPart data={data} isLoading={isLoading} />}
        </FiltersContextProvider>
    );
}

export interface ScenariosViewProps extends ActionsProps {
    table?: boolean;
}

export const ScenariosView = ({ table, ...props }: ScenariosViewProps) => (
    <Scenarios table={table}>
        <Actions {...props} />
    </Scenarios>
);
