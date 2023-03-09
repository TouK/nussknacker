import { Button, ButtonGroup } from "@mui/material";
import { NavLink } from "react-router-dom";
import React from "react";

export const Navigation = () => (
    <ButtonGroup fullWidth size="small">
        <Button component={NavLink} to="/scenarios">
            scenarios
        </Button>
        <Button component={NavLink} to="/scenarios/table">
            scenarios (table)
        </Button>
        <Button component={NavLink} to="/components">
            components
        </Button>
    </ButtonGroup>
);
