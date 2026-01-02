declare global {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    type $TodoType = any;
}

export type UnknownRecord = Record<string, unknown>;
export type UnknownFunction = (...args: unknown[]) => unknown;

export type WithId<T, K extends string = "id"> = T & { [key in K]: string };

//from BE comes as date in zulu time
export type Instant = string;
