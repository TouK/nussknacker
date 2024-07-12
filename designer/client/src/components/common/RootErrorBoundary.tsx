import React, { PropsWithChildren } from "react";
import { css, styled, Typography } from "@mui/material";
import ProblemOccurredSvg from "./problem-occurred.svg";
import { ErrorBoundary as ErrorBoundaryLibrary, ErrorBoundaryProps } from "react-error-boundary";
import { t } from "i18next";
import { LoadingButton } from "../../windowManager/LoadingButton";

type RootErrorPageProps = {
    message: string;
    description: string;
};

const StylesWrapper = styled("div")(
    ({ theme }) => css`
        height: 100%;
        width: 100%;
        padding: 0;
        margin: 0;
        display: grid;
        align-items: center;
        justify-items: center;
        color: ${theme.palette.text.primary};
        background: ${theme.palette.background.default};

        .position-wrapper {
            --top-margin: 8vh;
            --bottom-margin: 3vh;
            --side-margin: 5vw;
            position: relative;
            margin: var(--top-margin) var(--side-margin) var(--bottom-margin);
        }

        .text-position {
            container-type: size;
            position: absolute;
            top: 0px;
            left: 0;
            right: 0;
        }

        .text {
            font-weight: 300;
            font-size: 2.1 cqi;
        }

        h1,
        h2 {
            font-weight: inherit;
            margin-block-start: 0;
            margin-block-end: 0;
        }

        h1 {
            font-size: 2em;
        }

        h2 {
            font-size: 1.42em;
        }

        button {
            margin: 45px 0;
        }

        .artwork {
            --top-margin: 8vh;
            --bottom-margin: 3vh;
            --side-margin: 5vw;
            max-width: calc(100vw - 2 * var(--side-margin));
            max-height: calc(100vh - var(--top-margin) - var(--bottom-margin));
            width: 100vmin;
        }
    `,
);

export function RootErrorPage({ message, description, children }: PropsWithChildren<RootErrorPageProps>): JSX.Element {
    return (
        <StylesWrapper>
            <div className="position-wrapper">
                <div className="text-position">
                    <Typography variant={"h1"}>{message}</Typography>
                    <Typography variant={"h2"}>{description}</Typography>
                    {children}
                </div>
                <ProblemOccurredSvg className="artwork" />
            </div>
        </StylesWrapper>
    );
}

export default function RootErrorBoundary({ children }: PropsWithChildren<Partial<ErrorBoundaryProps>>): JSX.Element {
    const fallbackComponent = () => (
        <RootErrorPage
            message={t("error.UnexpectedError.message", "Unexpected error occurred.")}
            description={t(
                "error.UnexpectedError.description",
                "Please refresh the page. If the problem persists, please contact your system administrator.",
            )}
        >
            <LoadingButton title={t("InitializeError.buttonLabel", "Refresh the page")} action={() => window.location.reload()} />
        </RootErrorPage>
    );

    return <ErrorBoundaryLibrary FallbackComponent={fallbackComponent}>{children}</ErrorBoundaryLibrary>;
}
