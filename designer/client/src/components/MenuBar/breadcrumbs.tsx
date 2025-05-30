import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";
import { LoadingButton } from "@mui/lab";
import { Box, Breadcrumbs as MuiBreadcrumbs, Menu, MenuItem, Typography } from "@mui/material";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { NavLink } from "react-router-dom";

import { EnvironmentTag } from "../../containers/EnvironmentTag";
import { ScenariosBasePath } from "../../containers/paths";
import { fetchScenarios, getScenariosNames } from "../../reducers/scenarios";
import { getProcessName } from "../../reducers/selectors/graph";

export const Breadcrumbs = () => {
    const scenarioName = useSelector(getProcessName);
    const scenarioNames = useSelector(getScenariosNames);

    const [anchorEl, setAnchorEl] = useState(null);
    const open = Boolean(anchorEl);
    const dispatch = useDispatch();

    const handleClick = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = useCallback(() => {
        // dispatch({ type: "PROCESS_LOADING" });
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
                            {scenarioNames.map((name, index) => (
                                <MenuItem
                                    disabled={scenarioName === name}
                                    key={index}
                                    onClick={handleClose}
                                    component={"a"}
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
