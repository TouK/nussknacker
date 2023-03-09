import React, {ComponentType, DetailedHTMLProps, HTMLAttributes} from "react"
import loadable from "@loadable/component"
import ErrorBoundary from "react-error-boundary"
import {absoluteBePath} from "../common/UrlUtils"
import styled from "@emotion/styled"

const urlRe = /^(((https?:)?\/)?\/)?\w.*\.svg$/i

const AsyncSvg = loadable.lib(async ({src}: { src: string }) => {
  const urlMatch = src.match(urlRe)

  if (!urlMatch) {
    throw `${src} is not svg path`
  }

  if (urlMatch[1]) {
    const url = urlMatch[2] ? src : absoluteBePath(src)
    const response = await fetch(url)
    return await response.text()
  }

  // assume not absolute paths as local webpack paths
  const module = await import(`!raw-loader!../assets/img/${src}`)
  return module.default
}, {
  cacheKey: ({src}) => src,
})

const Flex = styled.div({
  display: "flex",
})

export interface InlineSvgProps extends DetailedHTMLProps<HTMLAttributes<HTMLDivElement>, HTMLDivElement> {
  src: string,
  FallbackComponent?: ComponentType,
}

export const InlineSvg = ({FallbackComponent, src, ...rest}: InlineSvgProps): JSX.Element => (
  <ErrorBoundary FallbackComponent={FallbackComponent}>
    <AsyncSvg src={src}>
      {(__html => <Flex {...rest} dangerouslySetInnerHTML={{__html}}/>)}
    </AsyncSvg>
  </ErrorBoundary>
)
