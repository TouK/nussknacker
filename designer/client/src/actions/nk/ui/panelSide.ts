export const enum PanelSide {
    Right = "RIGHT",
    Left = "LEFT",
    RightDynamic = "RIGHT_DYNAMIC",
    LeftDynamic = "LEFT_DYNAMIC",
}

export function isDynamic(side: PanelSide): boolean {
    return [PanelSide.RightDynamic, PanelSide.LeftDynamic].includes(side);
}

export function isLeft(side: PanelSide): boolean {
    return [PanelSide.Left, PanelSide.LeftDynamic].includes(side);
}

export function isRight(side: PanelSide): boolean {
    return [PanelSide.Right, PanelSide.RightDynamic].includes(side);
}
