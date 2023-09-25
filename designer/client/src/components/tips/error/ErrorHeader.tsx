import React from "react";

export const ErrorHeader = ({ message, className }: { message: string; className?: string }) => {
    return <span className={className}>{message}</span>;
};
