import * as  queryString from "query-string"
import React from "react"
import {withRouter} from "react-router-dom"
import {Tab, TabList, TabPanel, Tabs} from "react-tabs"
import "react-tabs/style/react-tabs.css"
import {nkPath} from "../config"
import {CustomProcessesPage, key, header} from "./admin/CustomProcesses"
import SearchComponents from "./admin/SearchComponents"
import Services from "./admin/Services"
import UnusedComponents from "./admin/UnusedComponents"
import {Page} from "./Page"

export class AdminPage extends React.Component {
  tabs = [
    {key: SearchComponents.key, title: SearchComponents.header, component: <SearchComponents/>},
    {key: UnusedComponents.key, title: UnusedComponents.header, component: <UnusedComponents/>},
    {key: Services.key, title: Services.header, component: <Services/>},
    {key: key, title: header, component: <CustomProcessesPage/>},
  ]

  constructor(props) {
    super(props)

    const query = queryString.parse(window.location.search, {parseNumbers: true})

    this.state = {
      selectedTab: query.tab || 0,
    }
  }

  onTabChange = (index) => {
    const params = {tab: index}
    this.props.history.replace({search: queryString.stringify(params)})
    this.setState(params)
  }

  render() {
    return (
      <Page>
        <Tabs defaultIndex={this.state.selectedTab} onSelect={this.onTabChange}>
          <TabList>
            {
              this.tabs.map(tab => {
                return (
                  <Tab key={tab.key}>{tab.title}</Tab>
                )
              })
            }
          </TabList>
          {
            this.tabs.map(tab => {
              return (
                <TabPanel key={tab.key}>{tab.component}</TabPanel>
              )
            })
          }
        </Tabs>
      </Page>
    )
  }
}

AdminPage.path = `${nkPath}/admin`
AdminPage.header = "Admin"

export default withRouter(AdminPage)
