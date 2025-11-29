import type { Prettify } from "../../useNodeTypeDetailsContentLogic";
import type { EditorProps } from "./Editor";

type _Error<Message extends string, Details extends NonNullable<unknown> = unknown> = Prettify<
    {
        "#error": Message;
    } & Details
>;

type Error<
    Message extends string,
    Passed extends NonNullable<unknown> = unknown,
    Details extends NonNullable<unknown> = unknown,
> = Passed extends _Error<string> ? Passed : _Error<Message, Details>;

export type PrintMissingProps<P = EditorProps, E = EditorProps> = Error<`Missing some EditorProps`, P, Omit<E, keyof P>>;

type PrintNotAllowedProps<P, E> = Error<"Not allowed props", P, Omit<P, keyof Omit<P, keyof E>>>;

type SplitToUnion<T> = {
    [K in keyof T]: { [P in K]: T[K] };
}[keyof T];

export type WithoutDeprecated<P, E> = P extends SplitToUnion<E> ? PrintNotAllowedProps<P, E> : P & EditorProps;
