import { Avatar, Button, SpeedDial, SpeedDialAction, SpeedDialIcon, Stack, styled } from "@mui/material";
import type { CSSProperties, PropsWithChildren } from "react";
import React, { useCallback, useEffect, useState } from "react";
import { useInViewRef, useMutationObserverRef } from "rooks";

import FragmentIcon from "../assets/icons/fragment.svg";
import ScanarioIcon from "../assets/icons/scenario.svg";
import { FiltersContextProvider } from "../common";
import { FiltersPart } from "./filters";
import type { ScenariosFiltersModel } from "./filters/scenariosFiltersModel";
import { ListPart } from "./list/listPart";
import { TablePart } from "./list/tablePart";
import { useScenariosWithStatus } from "./useScenariosQuery";

const StyledSpeedDial = styled(SpeedDial)(({ theme }) => {
    return {
        position: "fixed",
        transition: theme.transitions.create("padding-bottom"),
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
    };
});

export interface ActionsProps {
    addScenario?: () => void;
    addFragment?: () => void;
}

function useAssistantButtonAdjustment(inView: boolean): CSSProperties {
    const [paddingBottom, setPaddingBottom] = useState(0);

    const adjustPosition = useCallback(() => {
        const assistantButton = document.getElementById("ai-assistant-button");
        setPaddingBottom(assistantButton?.parentElement.hasAttribute("data-originalposition") ? assistantButton.offsetHeight : 0);
    }, []);

    const [ref] = useMutationObserverRef(adjustPosition, { attributes: true, attributeFilter: ["data-originalposition"], subtree: true });

    useEffect(() => {
        if (inView) {
            ref(null);
        } else {
            ref(document.getElementById("ai-assistant-button")?.parentElement);
            adjustPosition();
        }
    }, [adjustPosition, inView, ref]);

    return { paddingBottom };
}

const ActionAvatar = styled(Avatar)(({ theme }) => ({
    color: theme.palette.getContrastText(theme.palette.grey["800"]),
    bgcolor: theme.palette.grey["800"],
    "& > svg": {
        width: "1em",
        height: "1em",
    },
}));

function Actions({ addScenario, addFragment }: ActionsProps): JSX.Element {
    const [ref, inView] = useInViewRef();
    const { paddingBottom } = useAssistantButtonAdjustment(inView);

    if (!addScenario && !addFragment) {
        return null;
    }

    return (
        <>
            <Stack ref={ref} direction="row" spacing={1} sx={{ justifyContent: "flex-end" }}>
                {addScenario && (
                    <Button variant="outlined" size={"small"} onClick={addScenario}>
                        Add New scenario
                    </Button>
                )}
                {addFragment && (
                    <Button variant="outlined" size={"small"} onClick={addFragment}>
                        Add New fragment
                    </Button>
                )}
            </Stack>
            <StyledSpeedDial ariaLabel="Create new..." icon={<SpeedDialIcon />} TransitionProps={{ in: !inView }} style={{ paddingBottom }}>
                {addFragment && (
                    <SpeedDialAction
                        tooltipTitle={"New fragment"}
                        onClick={addFragment}
                        icon={
                            <ActionAvatar>
                                <FragmentIcon />
                            </ActionAvatar>
                        }
                    />
                )}
                {addScenario && (
                    <SpeedDialAction
                        tooltipTitle={"New scenario"}
                        onClick={addScenario}
                        icon={
                            <ActionAvatar>
                                <ScanarioIcon />
                            </ActionAvatar>
                        }
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
