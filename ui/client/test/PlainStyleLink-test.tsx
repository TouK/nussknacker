import Adapter from "@wojtekmaj/enzyme-adapter-react-17"
import Enzyme, {mount} from "enzyme"
import React from "react"
import {MemoryRouter} from "react-router"
import {isExternalUrl, PlainStyleLink} from "../containers/plainStyleLink"

const Link = props => (
  <MemoryRouter>
    <PlainStyleLink {...props} />
  </MemoryRouter>
)

describe("PlainStyleLink", () => {
  Enzyme.configure({adapter: new Adapter()})

  describe("isExternalUrl", () => {
    it("should pass http://", () => {
      expect(isExternalUrl("http://google.com")).toBeTruthy()
    })
    it("should pass https://", () => {
      expect(isExternalUrl("https://google.com")).toBeTruthy()
    })
    it("should pass //", () => {
      expect(isExternalUrl("//google.com")).toBeTruthy()
    })
    it("should drop /", () => {
      expect(isExternalUrl("/google")).toBeFalsy()
    })
    it("should drop ?", () => {
      expect(isExternalUrl("?google")).toBeFalsy()
    })
    it("should drop #", () => {
      expect(isExternalUrl("#google")).toBeFalsy()
    })
    it("should drop other", () => {
      expect(isExternalUrl("google")).toBeFalsy()
    })
  })

  it("should support http://", () => {
    expect(mount(<Link to={"http://google.com"} />).html()).toMatchSnapshot()
  })
  it("should support https://", () => {
    expect(mount(<Link to={"https://google.com"} />).html()).toMatchSnapshot()
  })
  it("should support //", () => {
    expect(mount(<Link to={"//google.com"} />).html()).toMatchSnapshot()
  })
  it("should support /", () => {
    expect(mount(<Link to={"/google"} />).html()).toMatchSnapshot()
  })
  it("should suport ?", () => {
    expect(mount(<Link to={"?google"} />).html()).toMatchSnapshot()
  })
  it("should support #", () => {
    expect(mount(<Link to={"#google"} />).html()).toMatchSnapshot()
  })
  it("should support plain string", () => {
    expect(mount(<Link to={"google"} />).html()).toMatchSnapshot()
  })
})

