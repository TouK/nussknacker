declare const __DEV__: boolean;
declare const __BUILD_VERSION__: string;
declare const __BUILD_HASH__: string;

declare module "*.styl" {
    const classes: { [key: string]: string };
    export default classes;
}

declare module "*.svg" {
    const content: string;
    export const ReactComponent: React.FC<React.SVGProps<SVGSVGElement>>;
    export default content;
}

declare module "!raw-loader!*" {
    const content: string;
    export default content;
}
