import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import { LoadingButton } from "@mui/lab";
import { Box, Breadcrumbs as MuiBreadcrumbs, Menu, MenuItem, Typography } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { NavLink } from "react-router-dom";

import { EnvironmentTag } from "../../containers/EnvironmentTag";
import { ScenariosBasePath } from "../../containers/paths";
import { fetchScenarios, getActiveScenariosNames } from "../../reducers/scenarios";
import { getProcessName } from "../../reducers/selectors/graph";

export const Breadcrumbs = () => {
    const scenarioName = useSelector(getProcessName);
    const scenarioNames = useSelector(getActiveScenariosNames);

    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const open = Boolean(anchorEl);
    const dispatch = useDispatch();

    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = useCallback(() => {
        setAnchorEl(null);
    }, []);

    const breadcrumbs = useMemo(() => {
        const basicBreadcrumbs = [
            <Typography key="environmentTag" variant="body2">
                <EnvironmentTag />
            </Typography>,
            <Typography key="scenarios" variant="body2">
                <Box component={NavLink} color="inherit" to={ScenariosBasePath} sx={{ fontWeight: "bold" }}>
                    Scenarios
                </Box>
            </Typography>,
        ];

        if (scenarioName) {
            basicBreadcrumbs.push(
                <Typography key="scenarioName" variant="body2">
                    <>
                        <LoadingButton
                            onClick={handleClick}
                            endIcon={<ArrowDropDownIcon />}
                            sx={{
                                color: "text.secondary",
                                textTransform: "none",
                                padding: 0,
                                minWidth: "auto",
                            }}
                        >
                            {scenarioName}
                        </LoadingButton>
                        <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
                            {scenarioNames.map((name) => (
                                <MenuItem
                                    disabled={scenarioName === name}
                                    key={name}
                                    onClick={handleClose}
                                    component={"a"}
                                    //TODO: For now, when we use SPA navigation via "to:" there is bad UX because of lack of loaders on scenario change.
                                    // For now it's better to just fully reload a page
                                    href={`/visualization/${name}`}
                                >
                                    {name}
                                </MenuItem>
                            ))}
                        </Menu>
                    </>
                </Typography>,
            );
        }
        return basicBreadcrumbs;
    }, [anchorEl, handleClose, open, scenarioName, scenarioNames]);

    useEffect(() => {
        dispatch(fetchScenarios());
    }, [dispatch]);

    return (
        <MuiBreadcrumbs separator={<NavigateNextIcon fontSize="inherit" />} aria-label="breadcrumb">
            {breadcrumbs}
        </MuiBreadcrumbs>
    );
};
