import type { Theme } from "@mui/material";
import { Box, styled } from "@mui/material";

type DynamicLabelProps = {
    label: string;
    hovered: boolean;
};

const labelBaseStyles = ({ theme }: { theme: Theme }) => ({
    position: "absolute",
    bottom: "100%",
    marginBottom: theme.spacing(0.75),
    ...theme.typography.overline,
    color: theme.palette.text.disabled,
});

export const NonDraggableLabel = styled(Box, {
    shouldForwardProp: (propName: string) => !["text", "hovered"].includes(propName),
})<DynamicLabelProps>(({ theme, label, hovered }) => ({
    "&::before": {
        ...labelBaseStyles({ theme }),
        content: hovered ? `'${label}'` : "unset",
    },
}));

export const DynamicLabel = styled(Box, {
    shouldForwardProp: (propName: string) => !["text", "hovered"].includes(propName),
})<DynamicLabelProps>(({ theme, label, hovered }) => ({
    "&::before": {
        ...labelBaseStyles({ theme }),
        "[data-testid^='draggable']:first-of-type &": {
            content: hovered ? "unset" : `'${label}'`,
        },

        "[data-testid^='draggable'][style*='fixed'] &": {
            content: hovered ? `'${label}'` : "unset",
        },
    },
}));
