import React, { ComponentType, SVGProps } from "react";
import { styledIcon } from "./Styled";

export default function ValidTip({ icon: Icon, message }: { icon: ComponentType<SVGProps<SVGSVGElement>>; message: string }) {
    const StyledIcon = styledIcon(Icon);
    return (
        <div style={{ marginBottom: "5px" }}>
            <StyledIcon />
            <span>{message}</span>
        </div>
    );
}
