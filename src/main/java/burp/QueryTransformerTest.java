package burp;

import graphql.GraphQLError;
import graphql.language.*;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.errors.NonSDLDefinitionError;
import graphql.schema.idl.errors.SchemaProblem;

import java.util.ArrayList;
import java.util.List;

import static graphql.parser.Parser.parse;
import static graphql.schema.idl.UnExecutableSchemaGenerator.makeUnExecutableSchema;

public class QueryTransformerTest {
    public static TypeDefinitionRegistry buildRegistry(Document document) {
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

    public static void main(String[] args) {

        String documentString = """
        type Query {}
        scalar UnknownScalar
        """;

        String queryString = """
                {
                  launchesPast(limit: 10) {
                    mission_name
                    launch_date_local
                    launch_site {
                      site_name_long
                    }
                    links {
                      article_link
                      video_link
                    }
                    rocket {
                      rocket_name
                      first_stage {
                        cores {
                          flight
                          core {
                            reuse_count
                            status
                          }
                        }
                      }
                      second_stage {
                        payloads {
                          payload_type
                          payload_mass_kg
                          payload_mass_lbs
                        }
                      }
                    }
                    ships {
                      name
                      home_port
                      image
                    }
                  }
                }
                fragment postData on Post {
                  id
                  title
                  text
                  author {
                    username
                    displayName
                  }
                  ... on Category {
                      name
                      id
                  }
                }
                query getPost($author: String!) {
                  getPosts(author: $author) {
                    post {
                      ...postData
                    }
                  }
                }
                     
                 """;

        //Parse input schema and query into an AST
        Document queryDocument = parse(queryString);
        Document schemaDocument = parse(documentString);

        // Modify input schema AST with input query AST
        QueryTransformer qt = new QueryTransformer();
        schemaDocument = qt.transform(schemaDocument, queryDocument);

        // Build GraphQL schema from updated schema AST and print
        GraphQLSchema schema = makeUnExecutableSchema(buildRegistry(schemaDocument));
        SchemaPrinter printer = new SchemaPrinter();
        System.out.println(printer.print(schema));
    }
}
