package burp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.SDLDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.NonSDLDefinitionError;
import graphql.schema.idl.errors.SchemaProblem;
import org.json.JSONObject;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import static graphql.introspection.IntrospectionQuery.INTROSPECTION_QUERY;
import static graphql.parser.Parser.parse;
import static graphql.schema.idl.UnExecutableSchemaGenerator.makeUnExecutableSchema;

public class IntrospectionEmulator {

    private Boolean enabled;
    private Boolean isSourceProxy;
    private URL targetProxyURL;
    private TypeDefinitionRegistry fileTypeRegistry;

    private List<String> customHeaders;
    private Document proxySchemaDocument;
    private static SchemaParser schemaParser;
    private static SchemaPrinter schemaPrinter;
    private final ExecutionInput introspectionQuery;

    private static TypeDefinitionRegistry buildRegistry(Document document) {
        List<GraphQLError> errors = new ArrayList<>();
        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        List<Definition> definitions = document.getDefinitions();
        for (Definition definition : definitions) {
            if (definition instanceof SDLDefinition) {
                typeRegistry.add((SDLDefinition) definition).ifPresent(errors::add);
            } else {
                errors.add(new NonSDLDefinitionError(definition));
            }
        }
        if (errors.size() > 0) {
            throw new SchemaProblem(errors);
        } else {
            return typeRegistry;
        }
    }

    public void replaceCustomHeaders(String headers) {
        customHeaders = new ArrayList<>();
        List<String> items = Arrays.asList(headers.split("\\n"));

        if (items.size() > 0) {
            customHeaders.addAll(items);
        }
    }

    public void resetCustomHeaders() {
        customHeaders = Collections.emptyList();
    }

    public List<String> getCustomHeaders() {
        return customHeaders;
    }

    public void setSourceProxy(Boolean state) {
        isSourceProxy = state;
    }

    public Boolean isSourceProxy() {
        return isSourceProxy;
    }
    public void setTargetProxy(String url) {
        try {
            targetProxyURL = new URL(url);
        }
        catch (Exception e) {
            targetProxyURL = null;
        }
    }
    public Boolean matchesTargetProxy(URL url){
        return url.equals(targetProxyURL);
    }
    public Boolean isEnabled() {
        return enabled;
    }

    public String getSdlSchema() {

        GraphQLSchema schema;

        try {
            if (isSourceProxy()) {
                schema = makeUnExecutableSchema(buildRegistry(proxySchemaDocument));
            }
            else {
                schema = makeUnExecutableSchema(fileTypeRegistry);
            }
        }
        catch (Exception e) {
            return "";
        }

        return schemaPrinter.print(schema);
    }

    public String getJsonSchema() {
        return introspect();
    }

    public void setState(Boolean state) {
        enabled = state;
    }

    ArrayBlockingQueue<String> queryTransformerQueue = new ArrayBlockingQueue<>(100, true);

    QueryTransformer queryTransformer = new QueryTransformer();

    private void processQueryQueue(){
        while (true) {
            try {
                // Create an AST of the query
                Document queryDocument = parse(queryTransformerQueue.take());

                // Modify input schema AST with input query AST
                proxySchemaDocument = queryTransformer.transform(proxySchemaDocument, queryDocument);

            } catch (Exception e) {
            }
        }
    }

    public void transformAndMergeQuery(String query) {
        try {
            queryTransformerQueue.put(query);
        }

        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void replaceSchema(String schema){
        resetGraphql();

        try {
            replaceSdl(schema);
        } catch(Exception e){
            replaceJson(schema);
        }

        // Try to build just to give the user feedback in the UI
        GraphQLSchema graphqlSchema = makeUnExecutableSchema(fileTypeRegistry);
        GraphQL.newGraphQL(graphqlSchema).build();
    }

    public void resetGraphql() {
        if (isSourceProxy()) {
            queryTransformerQueue.clear();
            String documentString = """
                        type Query {}
                        scalar UnknownScalar
                        """;
            proxySchemaDocument = parse(documentString);
        }

        else {
            fileTypeRegistry = null;
        }
    }

    public void replaceSdl(String sdl) {
        fileTypeRegistry = schemaParser.parse(sdl);
    }

    public void replaceJson(String json) {
        JsonObject jsonParsed;

        try {
            jsonParsed = new Gson().fromJson(json, JsonObject.class);
        } catch(Exception e) {
            throw e;
        }

        Map<String, Object> jsonSchema;

        // If the root node is not data then assume it's __schema
        if (jsonParsed.get("data") != null) {
            jsonSchema = new Gson().fromJson(
                    jsonParsed.get("data"), new TypeToken<HashMap<String, Object>>() {}.getType()
            );
        }

        else {
            jsonSchema = new Gson().fromJson(
                    jsonParsed, new TypeToken<HashMap<String, Object>>() {}.getType()
            );
        }

        Document jsonDocument = new IntrospectionResultToSchema().createSchemaDefinition(jsonSchema);
        fileTypeRegistry = schemaParser.parse(schemaPrinter.print(jsonDocument));
    }

    public String introspect() {
        GraphQLSchema schema;
        GraphQL graphQL;


        try {
            if (isSourceProxy()) {
                schema = makeUnExecutableSchema(buildRegistry(proxySchemaDocument));
            }
            else {
                schema = makeUnExecutableSchema(fileTypeRegistry);
            }
        }
        catch (Exception e) {
            return "";
        }

        graphQL = GraphQL.newGraphQL(schema).build();

        if (graphQL != null) {
            Map<String, Object> introspectionResult = graphQL.execute(introspectionQuery).toSpecification();
            JSONObject json = new JSONObject(introspectionResult);
            return json.toString(4);
        }

        else {
            return "";
        }
    }

    public IntrospectionEmulator() {
        enabled = false;
        isSourceProxy = false;
        customHeaders = new ArrayList<>();
        schemaParser = new SchemaParser();
        schemaPrinter = new SchemaPrinter();
        introspectionQuery = ExecutionInput.newExecutionInput().query(INTROSPECTION_QUERY).build();

        // Minimum types required for proxy detection
        String documentString = """
                        type Query {}
                        scalar UnknownScalar
                        """;
        proxySchemaDocument = parse(documentString);

        // Process the query queue in the background
        Runnable backgroundQueueProcessor = this::processQueryQueue;
        new Thread(backgroundQueueProcessor).start();
    }
}
