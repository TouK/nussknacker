import { useComponentQuery } from "../useComponentsQuery";
import { useBackHref } from "../../common";
import React from "react";
import { ViewList } from "@mui/icons-material";
import { Breadcrumbs as MuiBreadcrumbs, Link, Skeleton, Typography } from "@mui/material";
import { Link as RouterLink, Navigate, useParams } from "react-router-dom";

export function Breadcrumbs(): JSX.Element {
    const { componentId } = useParams<"componentId">();
    const { data: component, isLoading: componentLoading } = useComponentQuery(componentId);
    const back = useBackHref();

    return (
        <MuiBreadcrumbs aria-label="breadcrumb" sx={{ color: (theme) => theme.palette.getContrastText(theme.palette.background.default) }}>
            <Link component={RouterLink} to={back} underline="hover" color="inherit" sx={{ display: "flex", alignItems: "center" }}>
                <ViewList sx={{ mr: 0.5 }} fontSize="inherit" />
                Components
            </Link>
            <Typography color="inherit">
                {componentLoading ? (
                    <Skeleton width={componentId.length * 6} animation="wave" />
                ) : (
                    <>{component ? <strong>{component.name}</strong> : <Navigate replace to="/404" />}</>
                )}
            </Typography>
            <Typography color="inherit">usages</Typography>
        </MuiBreadcrumbs>
    );
}
