# GraphQuail

<p align="left">
  <img src="https://i.snap.as/0papNEuB.png" width="250"/>
</p>

GraphQuail is a Burp Suite extension that offers a toolkit for testing GraphQL endpoints. Here are the features currently implemented:

* Detection and building of a GraphQL schema from proxy traffic (and emulation of introspection query responses)
* Ability to add GraphiQL and Voyager to your endpoint right in your browser
* Introspection emulation with support for SDL and JSON schemas
* Custom headers injection for requests made from GraphiQL
* Context menus that let you extract GraphQL queries from requests

## Features Backlog

These are features we would like to implement eventually.

* [ ] Support GraphQL GET requests and form POST bodies
* [ ] Active mode for proxy schema detection (using `__typename` to determine the real types)   
* [ ] Active mode for schema detection using error feedback, like [clairvoyance](https://github.com/nikitastupin/clairvoyance)
* [ ] Auto refresh option in GraphiQl and Voyager
* [ ] Send query from repeater to GraphiQL and vice-versa 
* [ ] Passive and active Burp Suite findings such as recursion DoS
* [ ] Proxy query transformer log for debugging

## Usage

If you don't build your own JAR, you can use an already built one from the releases section. Refer to Burp Suite documentation for installing an extension. This extension is not currently hosted on BApp Store.

### GraphiQL and Voyager

Sometimes you want to be able to easily use GraphiQL or Voyager within your browser against a GraphQL endpoint. This gives you the ability to easily make requests using cookie authentication and the ability to add custom headers right within Burp Suite.

1. Enable GraphiQL and/or Voyager emulation
2. Click on the "Generate" button next to GraphiQL identifier or Voyager identifier. Alternatively set your own identifier and click "Set"
3. Visit your GraphQL endpoint in a browser with the identifier appended such as: `https://example.com/graphql/imxxgd`

Behind the scenes, the requests will be modified to go to the real GraphQL endpoint.

### Introspection Emulation

This is handy when the GraphQL endpoint doesn't have introspection enabled. If you haven't followed the steps in the GraphiQL and Voyager section yet, do that first.

1. Enable "Introspection Emulation"
2. Set the Schema Source to either: File or Proxy
3. If it is set to File, past the JSON or SDL schema in the box below and click on "Replace Schema". Otherwise past the exact GraphQL endpoint URL and click on "Set Target URL"
4. GraphiQL and Voyager will now receive an emulated introspection response when it is visited or refreshed

At any point you can reset the schema or copy it in JSON or SDL format.

## Building

Run `gradle build` and JAR will be generated and saved in `releases/`
