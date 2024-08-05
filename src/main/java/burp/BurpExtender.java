package burp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.awt.*;
import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BurpExtender implements IBurpExtender, ITab, ActionListener {
    private final PathEmulator pathEmulator = new PathEmulator();
    private final IntrospectionEmulator introspectionEmulator = new IntrospectionEmulator();
    private final ResponseEmulator responseEmulator = new ResponseEmulator();
    private final SuiteTab suiteTab = new SuiteTab();

    @Override
    public String getTabCaption() {
        return "GraphQuail";
    }

    @Override
    public Component getUiComponent() {
        return suiteTab;
    }

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {

        // Register callbacks
        callbacks.setExtensionName("GraphQuail");
        callbacks.registerContextMenuFactory(new editorMenu(callbacks));
        callbacks.registerHttpListener(new emulatorListener(callbacks));

        // Set up GUI
        callbacks.addSuiteTab(this);
        suiteTab.setupListeners(this);
        suiteTab.enableRightPanel(false, introspectionEmulator.isSourceProxy());
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        // Commands for various GUI actions
        switch (e.getActionCommand()) {
            case "generateGraphiqlIdentifier":
                String graphiqlIdentifier = Utils.generateIdentifier(6);
                if (pathEmulator.setGraphiqlPath(graphiqlIdentifier)) {
                    suiteTab.graphiqlIdentifierTextField.setText(graphiqlIdentifier);
                }
                break;

            case "generateVoyagerIdentifier":
                String voyagerIdentifier = Utils.generateIdentifier(6);
                if (pathEmulator.setVoyagerPath(voyagerIdentifier)) {
                    suiteTab.voyagerIdentifierTextField.setText(voyagerIdentifier);
                }
                break;

            case "toggleGraphiqlEmu":
                Boolean state = suiteTab.graphiqlEmuCheckBox.isSelected();
                pathEmulator.setGraphiqlState(state);
                break;

            case "toggleVoyagerEmu":
                Boolean voyagerState = suiteTab.voyagerEmuCheckBox.isSelected();
                pathEmulator.setVoyagerState(voyagerState);
                break;

            case "setCustomGraphiqlIdentifier":
                String graphiqlCustomIdentifier = suiteTab.graphiqlIdentifierTextField.getText();
                if (!pathEmulator.setGraphiqlPath(graphiqlCustomIdentifier)) {
                    suiteTab.graphiqlIdentifierTextField.setText("");
                }
                break;

            case "setCustomVoyagerIdentifier":
                String voyagerCustomIdentifier = suiteTab.voyagerIdentifierTextField.getText();
                if (!pathEmulator.setVoyagerPath(voyagerCustomIdentifier)) {
                    suiteTab.voyagerIdentifierTextField.setText("");
                }
                break;

            case "toggleIntrospectionEmu":
                Boolean introspectionState = suiteTab.introspectionEmuCheckBox.isSelected();
                suiteTab.enableRightPanel(introspectionState, introspectionEmulator.isSourceProxy());
                introspectionEmulator.setState(introspectionState);
                break;

            case "replaceSchema":
                if (!suiteTab.schemaTextArea.getText().equals("")) {
                    try {
                        introspectionEmulator.replaceSchema(suiteTab.schemaTextArea.getText());
                    } catch(Exception error) {
                        suiteTab.introspectionErrorTextArea.setText(error.toString());
                    }
                }
                break;

            case "resetIntrospection":
                introspectionEmulator.resetGraphql();
                suiteTab.introspectionErrorTextArea.setText("");
                break;

            case "clearIntrospectionErrors":
                suiteTab.introspectionErrorTextArea.setText("");
                break;

            case "copySdlSchema":
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(introspectionEmulator.getSdlSchema()),null);
                break;

            case "copyJsonSchema":
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(introspectionEmulator.getJsonSchema()),null);
                break;

            case "setTargetUrl":
                introspectionEmulator.setTargetProxy(suiteTab.targetUrlTextField.getText());
                break;

            case "selectSourceProxy":
                suiteTab.setSchemaFile(false);
                suiteTab.setSchemaProxy(true);
                introspectionEmulator.setSourceProxy(true);
                break;

            case "selectSourceFile":
                suiteTab.setSchemaFile(true);
                suiteTab.setSchemaProxy(false);
                introspectionEmulator.setSourceProxy(false);
                break;

            case "replaceCustomHeaders":
                introspectionEmulator.replaceCustomHeaders(suiteTab.customHeadersTextField.getText());
                break;

            case "resetCustomHeaders":
                introspectionEmulator.resetCustomHeaders();
                break;
        }
    }


    static class editorMenu implements IContextMenuFactory {
        IBurpExtenderCallbacks extenderCallbacks;
        IExtensionHelpers extenderHelpers;

        @Override
        public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation) {

            switch (invocation.getInvocationContext()) {
                case IContextMenuInvocation.CONTEXT_MESSAGE_EDITOR_REQUEST, IContextMenuInvocation.CONTEXT_MESSAGE_VIEWER_REQUEST -> {
                }
                default -> {
                    return null;
                }
            }

            List<JMenuItem> menu = new ArrayList<>();

            JMenuItem copyQuery = new JMenuItem("Copy GraphQL query/mutation");
            copyQuery.addActionListener(actionEvent -> {
                String raw = "";
                byte[] request = invocation.getSelectedMessages()[0].getRequest();
                byte contentType = extenderHelpers.analyzeRequest(request).getContentType();

                int bodyOffset = extenderHelpers.analyzeRequest(request).getBodyOffset();

                String body = new String(request, bodyOffset, request.length - bodyOffset, StandardCharsets.UTF_8);

                if (contentType == IRequestInfo.CONTENT_TYPE_JSON) {
                    raw = Utils.jsonQueryToRaw(body);
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(raw),null);
            });

            menu.add(copyQuery);
            return menu;
        }

        public editorMenu(IBurpExtenderCallbacks callbacks) {
            extenderCallbacks = callbacks;
            extenderHelpers = callbacks.getHelpers();
        }
    }

    class emulatorListener implements IHttpListener {

        IBurpExtenderCallbacks extenderCallbacks;
        IExtensionHelpers extenderHelpers;
        IRequestInfo requestInfo;

        @Override
        public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
            if (toolFlag == IBurpExtenderCallbacks.TOOL_PROXY) {
                requestInfo = extenderHelpers.analyzeRequest(messageInfo);

                // Get request details
                String method = requestInfo.getMethod();
                byte[] request = messageInfo.getRequest();
                URL url = requestInfo.getUrl();
                String path = requestInfo.getUrl().getPath();

                if (messageIsRequest) {

                    // Get request body
                    int bodyOffset = requestInfo.getBodyOffset();

                    if (bodyOffset < request.length) {
                        String body = new String(request, bodyOffset, request.length - bodyOffset, StandardCharsets.UTF_8);

                        // Get request headers
                        String strHeaders = new String(request, 0, bodyOffset, StandardCharsets.UTF_8);
                        ArrayList<String> headers = new ArrayList<>();
                        Collections.addAll(headers, strHeaders.split("\\r?\\n"));

                        // Change all emulated paths to real GraphQL paths unless it's an emulated introspection request
                        if (method.equals("POST")) {
                            if (path.endsWith("/" + pathEmulator.getGraphiqlPath()) || path.endsWith("/" + pathEmulator.getVoyagerPath())) {

                                // First check if introspection is disabled, change to real path if it is
                                // Or if introspection is enabled, only change to real paths when it's not an introspection query
                                if (!introspectionEmulator.isEnabled() || !body.contains("__schema")) {
                                    String modifiedPath = headers.get(0).replace("/" + pathEmulator.getGraphiqlPath(), "");
                                    modifiedPath = modifiedPath.replace("/" + pathEmulator.getVoyagerPath(), "");
                                    modifiedPath = modifiedPath.replace("  ", " / ");
                                    headers.set(0, modifiedPath);
                                    headers.addAll(introspectionEmulator.getCustomHeaders());

                                    byte[] customRequest = extenderHelpers.buildHttpMessage(headers, body.getBytes());
                                    messageInfo.setRequest(customRequest);
                                }

                            }
                        }

                        // Parse query and send to QueryTransformer
                        if (introspectionEmulator.isEnabled()) {
                            if (method.equals("POST")) {
                                String graphQLQuery = "";
                                if (introspectionEmulator.isSourceProxy() && introspectionEmulator.matchesTargetProxy(url)) {
                                    JsonObject jsonObj;
                                    JsonArray jsonArr;

                                    try {
                                        // First try to parse the body as a JSON array
                                        jsonArr = new Gson().fromJson(body, JsonArray.class);

                                        for (JsonElement item : jsonArr) {
                                            JsonObject current = (JsonObject) item;
                                            if (current != null) {
                                                graphQLQuery = graphQLQuery + current.get("query").getAsString();
                                            }
                                        }
                                    } catch (Exception e) {
                                        try {
                                            // Doesn't seem like it's an array, so we parse the query in the root
                                            jsonObj = new Gson().fromJson(body, JsonObject.class);
                                            graphQLQuery = jsonObj.get("query").getAsString();
                                        } catch (Exception d) {
                                        }
                                    }

                                    if (!graphQLQuery.equals("")) {
                                        introspectionEmulator.transformAndMergeQuery(graphQLQuery);
                                    }
                                }
                            }
                        }
                    }
                }

                else {
                    // If this doesn't get changed then no modifications will happen
                    byte[] modifiedBody = null;
                    String statusCode = "200";
                    List<String> extraHeaders = new ArrayList<>();

                    // Check for emulated paths and modify the response if it exists
                    if (method.equals("GET")) {
                        // GraphiQL emulation
                        if (path.endsWith("/" + pathEmulator.getGraphiqlPath())) {
                            if (pathEmulator.graphiqlEnabled()) {
                                modifiedBody = responseEmulator.getGraphiql().getBytes();
                            }
                        }
                        // Voyager emulation
                        else if (path.endsWith("/" + pathEmulator.getVoyagerPath())) {
                            if (pathEmulator.voyagerEnabled()) {
                                modifiedBody = responseEmulator.getVoyager().getBytes();
                            }
                        }
                    }
                    else if (method.equals("POST")) {
                        // Introspection emulation
                        if (path.endsWith("/" + pathEmulator.getGraphiqlPath()) || path.endsWith("/" + pathEmulator.getVoyagerPath())) {
                            modifiedBody = introspectionEmulator.introspect().getBytes();
                        }
                    }

                    // If modifiedBody is not null then we matched an emulated path above
                    if (modifiedBody != null) {
                        List<String> customHeaders = new ArrayList<>();
                        customHeaders.add("HTTP/1.1 " + statusCode);
                        customHeaders.addAll(extraHeaders);
                        byte[] customResponse = extenderHelpers.buildHttpMessage(customHeaders, modifiedBody);
                        messageInfo.setResponse(customResponse);
                    }
                }
            }
        }

        public emulatorListener(IBurpExtenderCallbacks callbacks) {
            extenderCallbacks = callbacks;
            extenderHelpers = callbacks.getHelpers();
        }

    }
}

