package burp;

import graphql.language.*;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.List;

import static graphql.util.TreeTransformerUtil.changeNode;

public class QueryTransformer {
    // If you are trying to understand what goes on in here, I'm sorry.
    // This needs to be cleaned up...
    Document internalSchema;

    public static String appendType(String str, String parent) {
        return str + capitalize(parent) + "Type";
    }

    public static String capitalize(String str) {

        return str.substring(0, 1).toUpperCase() + str.toLowerCase().substring(1);
    }
    private List<FragmentDefinition> collectFragments(Document query) {
        List<FragmentDefinition> fragments = new ArrayList<>();
        NodeVisitorStub visitor = new NodeVisitorStub() {
            public TraversalControl visitFragmentDefinition(FragmentDefinition node, TraverserContext<Node> context) {
                // Add to our collection
                fragments.add(node);
                return visitDefinition(node, context);
            }
        };
        NodeTraverser traverser = new NodeTraverser();
        traverser.depthFirst(visitor, query);
        return fragments;
    }
    public Document transform(Document schema, Document query) {
        internalSchema = schema;
        List<FragmentDefinition> fragments = collectFragments(query);
        NodeVisitorStub visitor = new NodeVisitorStub() {

            public TraversalControl visitInlineFragment(InlineFragment node, TraverserContext<Node> context) {
                if (context.getParentContext().getParentContext().thisNode() instanceof Field) {
                    Field parent = (Field) context.getParentContext().getParentContext().thisNode();

                    String fragmentName = node.getTypeCondition().getName();

                    List<Selection> selections = node.getSelectionSet().getSelections();

                    for (Selection s : selections) {
                        if (s instanceof Field) {
                            Field f = (Field) s;

                            List<InputValueDefinition> arguments = new ArrayList<>();

                            // Include arguments in the new ObjectTypeDefinition if they exist
                            if (f.getArguments() != null) {
                                List<Argument> rawArguments = f.getArguments();

                                for (Argument current : rawArguments) {

                                    InputValueDefinition ivd;

                                    // Create a different types based on the argument
                                    if (current.getName().equalsIgnoreCase("input")) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName(f.getName() + "Input"));
                                        ObjectValue obj = (ObjectValue) current.getValue();

                                        // If we find ENUM types in the input, then also merge those into the internal schema
                                        for (ObjectField curField : obj.getObjectFields()) {
                                            if (curField.getValue() instanceof EnumValue) {
                                                EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                                        .name(((EnumValue) curField.getValue()).getName())
                                                        .build();
                                                EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                                        .name(f.getName() + capitalize(curField.getName()) + "Enum")
                                                        .enumValueDefinition(evd)
                                                        .build();
                                                internalSchema = merge(internalSchema, newNode);
                                            }
                                        }
                                    }
                                    else if (current.getValue() instanceof EnumValue) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName(f.getName() + capitalize(current.getName()) + "Enum"));

                                        // We have to create the Enum type as well
                                        EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                                .name(((EnumValue) current.getValue()).getName())
                                                .build();

                                        EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                                .name(f.getName() + capitalize(current.getName()) + "Enum")
                                                .enumValueDefinition(evd)
                                                .build();
                                        internalSchema = merge(internalSchema, newNode);
                                    }
                                    else if (current.getValue() instanceof StringValue) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName("String"));
                                    }
                                    else if (current.getValue() instanceof BooleanValue) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName("Boolean"));
                                    }
                                    else if (current.getValue() instanceof IntValue) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName("Int"));
                                    }
                                    else if (current.getValue() instanceof FloatValue) {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName("Float"));
                                    }
                                    else {
                                        ivd = new InputValueDefinition(current.getName(), new TypeName("UnknownScalar"));
                                    }
                                    arguments.add(ivd);
                                }
                            }

                            if (f.getSelectionSet() == null) {

                                if (!f.getName().equalsIgnoreCase("__typename")) {
                                    ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                            .name(fragmentName)
                                            .fieldDefinition(FieldDefinition.newFieldDefinition()
                                                    .name(f.getName())
                                                    .type(new TypeName("UnknownScalar"))
                                                    .inputValueDefinitions(arguments)
                                                    .build())
                                            .build();

                                    // Merge
                                    internalSchema = merge(internalSchema, newNode);
                                }
                            }

                            // If there's a selection set then we set the type to an object
                            else {
                                ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                        .name(fragmentName)
                                        .fieldDefinition(FieldDefinition.newFieldDefinition()
                                                .name(f.getName())
                                                .type(new TypeName(appendType(f.getName(), fragmentName)))
                                                .inputValueDefinitions(arguments)
                                                .build())
                                        .build();

                                // Merge
                                internalSchema = merge(internalSchema, newNode);
                            }

                            UnionTypeDefinition newUnionNode = UnionTypeDefinition.newUnionTypeDefinition()
                                    .name(parent.getName() + "Union")
                                    .memberType(new TypeName(fragmentName))
                                    .build();

                            // Merge
                            internalSchema = merge(internalSchema, newUnionNode);
                        }
                    }
                }
                return visitSelection(node, context);
            }
            public TraversalControl visitField(Field node, TraverserContext<Node> context) {

                // Default to Query, but use another operation type of it exists
                String operationType = "Query";
                if (context.getParentContext().getParentContext().thisNode() instanceof OperationDefinition) {
                    operationType = capitalize(((OperationDefinition) context.getParentContext().getParentContext().thisNode()).getOperation().name());
                }

                List<InputValueDefinition> arguments = new ArrayList<>();

                // Include arguments in the new ObjectTypeDefinition if they exist
                if (node.getArguments() != null) {
                    List<Argument> rawArguments = node.getArguments();

                    for (Argument current : rawArguments) {

                        InputValueDefinition ivd;

                        // Create a different types based on the argument
                        if (current.getName().equalsIgnoreCase("input")) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName(node.getName() + "Input"));
                            ObjectValue obj = (ObjectValue) current.getValue();

                            // If we find ENUM types in the input, then also merge those into the internal schema
                            for (ObjectField curField : obj.getObjectFields()) {
                                if (curField.getValue() instanceof EnumValue) {
                                    EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                            .name(((EnumValue) curField.getValue()).getName())
                                            .build();
                                    EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                            .name(node.getName() + capitalize(curField.getName()) + "Enum")
                                            .enumValueDefinition(evd)
                                            .build();
                                    internalSchema = merge(internalSchema, newNode);
                                }
                            }
                        }
                        else if (current.getValue() instanceof EnumValue) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName(node.getName() + capitalize(current.getName()) + "Enum"));

                            // We have to create the Enum type as well
                            EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                    .name(((EnumValue) current.getValue()).getName())
                                    .build();

                            EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                    .name(node.getName() + capitalize(current.getName()) + "Enum")
                                    .enumValueDefinition(evd)
                                    .build();
                            internalSchema = merge(internalSchema, newNode);
                        }
                        else if (current.getValue() instanceof StringValue) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName("String"));
                        }
                        else if (current.getValue() instanceof BooleanValue) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName("Boolean"));
                        }
                        else if (current.getValue() instanceof IntValue) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName("Int"));
                        }
                        else if (current.getValue() instanceof FloatValue) {
                            ivd = new InputValueDefinition(current.getName(), new TypeName("Float"));
                        }
                        else {
                            ivd = new InputValueDefinition(current.getName(), new TypeName("UnknownScalar"));
                        }
                        arguments.add(ivd);
                    }
                }

                // Build a Query ObjectTypeDefinition node containing the current field
                // Must contain a selectionSet and have a parent of OperationDefinition type to be considered a query
                if (node.getSelectionSet() != null && context.getParentContext().getParentContext().thisNode() instanceof OperationDefinition) {

                    String objectTypeName = appendType(node.getName(), "Query");

                    // If the selection  contains any InlineFragments, then we are going to consider it a union type
                    for (Selection selection : node.getSelectionSet().getSelections()) {
                        if (selection instanceof InlineFragment) {
                            objectTypeName = node.getName() + "Union";
                            break;
                        }
                    }

                    // Build new Query node
                    ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                            .name(operationType)
                            .fieldDefinition(FieldDefinition.newFieldDefinition()
                                    .name(node.getName())
                                    .type(new TypeName(objectTypeName))
                                    .inputValueDefinitions(arguments)
                                    .build())
                            .build();

                    internalSchema = merge(internalSchema, newNode);
                }

                // Build ObjectTypeDefinition node using the current field
                // Process as long as it has a parent
                if (context.getParentContext().getParentContext().thisNode() instanceof Field){

                    // Parent is a field
                    if (context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode() instanceof Field){
                        Field parent = (Field) context.getParentContext().getParentContext().thisNode();
                        Field parentsParent = (Field) context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode();
                        String typeGuess = "UnknownScalar";


                        // If there is a selectionSet then the type is not a Scalar or Enum
                        if (node.getSelectionSet() != null) {
                            typeGuess = appendType(node.getName(), parent.getName());

                            // If the selection  contains any InlineFragments, then we are going to consider it a union type
                            for (Selection selection : node.getSelectionSet().getSelections()) {
                                if (selection instanceof InlineFragment) {
                                    typeGuess = node.getName() + "Union";
                                    break;
                                }
                            }
                        }

                        // Build an ObjectTypeDefinition node from the information we have
                        ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(appendType(parent.getName(), parentsParent.getName()))
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name(node.getName())
                                        .type(new TypeName(typeGuess))
                                        .inputValueDefinitions(arguments)
                                        .build())
                                .build();

                        // Merge, but we don't want to include it if it's __typename
                        if (!node.getName().equalsIgnoreCase("__typename")) {
                            internalSchema = merge(internalSchema, newNode);
                        }
                    }

                    // Parent is an InlineFragment
                    else if (context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode() instanceof InlineFragment){
                        Field parent = (Field) context.getParentContext().getParentContext().thisNode();
                        InlineFragment parentsParent = (InlineFragment) context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode();
                        String typeGuess = "UnknownScalar";


                        // If there is a selectionSet then the type is not a Scalar or Enum
                        if (node.getSelectionSet() != null) {
                            typeGuess = appendType(node.getName(), parent.getName());

                            // If the selection  contains any InlineFragments, then we are going to consider it a union type
                            for (Selection selection : node.getSelectionSet().getSelections()) {
                                if (selection instanceof InlineFragment) {
                                    typeGuess = node.getName() + "Union";
                                    break;
                                }
                            }
                        }

                        // Build an ObjectTypeDefinition node from the information we have
                        ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(appendType(parent.getName(), parentsParent.getTypeCondition().getName()))
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name(node.getName())
                                        .type(new TypeName(typeGuess))
                                        .inputValueDefinitions(arguments)
                                        .build())
                                .build();

                        // Merge, but we don't want to include it if it's __typename
                        if (!node.getName().equalsIgnoreCase("__typename")) {
                            internalSchema = merge(internalSchema, newNode);
                        }
                    }


                    // Parent is an ObjectTypeDefinition
                    else {

                        String nodeName;

                        Field parent = (Field) context.getParentContext().getParentContext().thisNode();

                        try {
                            Field parentParents = (Field) context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode();
                            nodeName = appendType(parent.getName(), capitalize(parentParents.getName()) + "Query");
                        }

                        catch (Exception e) {
                            nodeName = appendType(parent.getName(), "Query");
                        }

                        String typeGuess = "UnknownScalar";

                        // If there is a selectionSet then the type is not a Scalar or Enum
                        if (node.getSelectionSet() != null) {
                            typeGuess = appendType(node.getName(), parent.getName());

                            // If the selection  contains any InlineFragments, then we are going to consider it a union type
                            for (Selection selection : node.getSelectionSet().getSelections()) {
                                if (selection instanceof InlineFragment) {
                                    typeGuess = node.getName() + "Union";
                                    break;
                                }
                            }
                        }

                        // Build an ObjectTypeDefinition node from the information we have
                        ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(nodeName)
                                .fieldDefinition(FieldDefinition.newFieldDefinition()
                                        .name(node.getName())
                                        .type(new TypeName(typeGuess))
                                        .inputValueDefinitions(arguments)
                                        .build())
                                .build();

                        // Merge, but we don't want to include it if it's __typename
                        if (!node.getName().equalsIgnoreCase("__typename")) {
                            internalSchema = merge(internalSchema, newNode);
                        }
                    }
                }

                return visitSelection(node, context);
            }

            public TraversalControl visitFragmentSpread(FragmentSpread node, TraverserContext<Node> context) {
                if (context.getParentContext().getParentContext().thisNode() instanceof Field) {
                    Field parent = (Field) context.getParentContext().getParentContext().thisNode();

                    String parentsParentName = "";

                    if (context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode() instanceof Field) {
                        Field parentsParent = (Field) context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode();
                        parentsParentName = parentsParent.getName();
                    }

                    else if (context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode() instanceof OperationDefinition) {
                        parentsParentName = "Query";
                    }

                    else if (context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode() instanceof InlineFragment) {
                        InlineFragment parentsParent = (InlineFragment) context.getParentContext().getParentContext().getParentContext().getParentContext().thisNode();
                        parentsParentName = parentsParent.getTypeCondition().getName();
                    }

                    String fragmentName = node.getName();
                    List<FieldDefinition> definitions = new ArrayList<>();

                    // Check our internal store of fragments to see if we get any matches
                    for (FragmentDefinition fragment : fragments) {
                        if (fragment.getName().equalsIgnoreCase(fragmentName)){
                            List<Selection> selections = fragment.getSelectionSet().getSelections();

                            // We found a matching fragment, so now we are creating FieldDefinitions to add
                            for  (Selection s : selections) {
                                if (s instanceof Field) {
                                    Field f = (Field) s;

                                    List<InputValueDefinition> arguments = new ArrayList<>();

                                    // Include arguments in the new ObjectTypeDefinition if they exist
                                    if (f.getArguments() != null) {
                                        List<Argument> rawArguments = f.getArguments();

                                        for (Argument current : rawArguments) {

                                            InputValueDefinition ivd;

                                            // Create a different types based on the argument
                                            if (current.getName().equalsIgnoreCase("input")) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName(f.getName() + "Input"));
                                                ObjectValue obj = (ObjectValue) current.getValue();

                                                // If we find ENUM types in the input, then also merge those into the internal schema
                                                for (ObjectField curField : obj.getObjectFields()) {
                                                    if (curField.getValue() instanceof EnumValue) {
                                                        EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                                                .name(((EnumValue) curField.getValue()).getName())
                                                                .build();
                                                        EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                                                .name(f.getName() + capitalize(curField.getName()) + "Enum")
                                                                .enumValueDefinition(evd)
                                                                .build();
                                                        internalSchema = merge(internalSchema, newNode);
                                                    }
                                                }
                                            }
                                            else if (current.getValue() instanceof EnumValue) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName(f.getName() + capitalize(current.getName()) + "Enum"));

                                                // We have to create the Enum type as well
                                                EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                                        .name(((EnumValue) current.getValue()).getName())
                                                        .build();

                                                EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                                        .name(f.getName() + capitalize(current.getName()) + "Enum")
                                                        .enumValueDefinition(evd)
                                                        .build();
                                                internalSchema = merge(internalSchema, newNode);
                                            }
                                            else if (current.getValue() instanceof StringValue) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName("String"));
                                            }
                                            else if (current.getValue() instanceof BooleanValue) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName("Boolean"));
                                            }
                                            else if (current.getValue() instanceof IntValue) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName("Int"));
                                            }
                                            else if (current.getValue() instanceof FloatValue) {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName("Float"));
                                            }
                                            else {
                                                ivd = new InputValueDefinition(current.getName(), new TypeName("UnknownScalar"));
                                            }
                                            arguments.add(ivd);
                                        }
                                    }

                                    if (!f.getName().equalsIgnoreCase("__typename")) {
                                        FieldDefinition fd = FieldDefinition.newFieldDefinition()
                                                .name(f.getName())
                                                .type(new TypeName("UnknownScalar"))
                                                .inputValueDefinitions(arguments)
                                                .build();
                                        definitions.add(fd);
                                    }
                                }
                            }
                            // Create a new ObjectTypeDefinition with fields discovered in the fragment
                            if (!parentsParentName.isEmpty()) {
                                System.out.println("NAMMEEE: " + appendType(parent.getName(), capitalize(parentsParentName)));
                                ObjectTypeDefinition newNode = ObjectTypeDefinition.newObjectTypeDefinition()
                                        .name(appendType(parent.getName(), capitalize(parentsParentName)))
                                        .fieldDefinitions(definitions)
                                        .build();
                                // Merge
                                internalSchema = merge(internalSchema, newNode);
                            }
                        }
                    }
                }
                return visitSelection(node, context);
            }

            public TraversalControl visitArgument(Argument node, TraverserContext<Node> context) {
                // Handle input: arguments
                if (node.getName().equalsIgnoreCase("input") && context.getParentContext().thisNode() instanceof Field) {
                    Field parent = (Field) context.getParentContext().thisNode();
                    List<InputValueDefinition> definitions = new ArrayList<>();

                    // Include ObjectFields in the new InputObjectTypeDefinition
                    if (node.getValue() != null) {
                        if (node.getValue() instanceof ObjectValue) {
                            List<ObjectField> rawValues = ((ObjectValue) node.getValue()).getObjectFields();

                            // Add each field inside the InputValueDefinition
                            for (ObjectField current : rawValues) {
                                InputValueDefinition ivd;

                                if (current.getValue() instanceof EnumValue) {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName(parent.getName() + capitalize(current.getName()) + "Enum"));

                                    // We have to create the Enum type as well
                                    EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                                            .name(((EnumValue) current.getValue()).getName())
                                            .build();

                                    EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                                            .name(parent.getName() + capitalize(current.getName()) + "Enum")
                                            .enumValueDefinition(evd)
                                            .build();
                                    internalSchema = merge(internalSchema, newNode);
                                }
                                else if (current.getValue() instanceof StringValue) {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName("String"));
                                }
                                else if (current.getValue() instanceof BooleanValue) {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName("Boolean"));
                                }
                                else if (current.getValue() instanceof IntValue) {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName("Int"));
                                }
                                else if (current.getValue() instanceof FloatValue) {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName("Float"));
                                }
                                else {
                                    ivd = new InputValueDefinition(current.getName(), new TypeName("UnknownScalar"));
                                }
                                definitions.add(ivd);
                            }
                        }
                    }

                    InputObjectTypeDefinition newNode = InputObjectTypeDefinition.newInputObjectDefinition()
                            .name(parent.getName() + "Input")
                            .inputValueDefinitions(definitions)
                            .build();
                    // Merge
                    internalSchema = merge(internalSchema, newNode);
                }

                // Handle enums
                else if (node.getValue() instanceof EnumValue && context.getParentContext().thisNode() instanceof Field) {
                    Field parent = (Field) context.getParentContext().thisNode();

                    EnumValueDefinition evd = EnumValueDefinition.newEnumValueDefinition()
                            .name(((EnumValue) node.getValue()).getName())
                            .build();

                    EnumTypeDefinition newNode = EnumTypeDefinition.newEnumTypeDefinition()
                            .name(parent.getName() + capitalize(node.getName())  + "Enum")
                            .enumValueDefinition(evd)
                            .build();

                    // Merge
                    internalSchema = merge(internalSchema, newNode);
                }
                return visitNode(node, context);
            }
        };
        NodeTraverser traverser = new NodeTraverser();
        traverser.depthFirst(visitor, query);
        return internalSchema;
    }

    public Document merge(Document schema, Node newNode) {

        NodeVisitorStub visitor = new NodeVisitorStub() {

            public TraversalControl visitObjectTypeDefinition(ObjectTypeDefinition node, TraverserContext<Node> context) {

                // We update definitions in an existing ObjectTypeDefinition
                // Only work with the current newNode if it's an ObjectTypeDefinition
                if (newNode instanceof ObjectTypeDefinition) {
                    ObjectTypeDefinition newNodeObj = (ObjectTypeDefinition) newNode;


                    // Only continue if the current node has the same name as newNode
                    if (node.getName().equalsIgnoreCase(newNodeObj.getName())) {
                        List<FieldDefinition> definitions = new ArrayList<>(node.getFieldDefinitions());

                        // Only add fields from newNode if it doesn't already exist within the current node
                        for (int i = 0; i < newNodeObj.getFieldDefinitions().size(); i++) {
                            Boolean exists = false;
                            FieldDefinition existingNode = null;

                            for (int j = 0; j < definitions.size(); j++) {
                                if (newNodeObj.getFieldDefinitions().get(i).getName().equalsIgnoreCase(definitions.get(j).getName())) {
                                    exists = true;
                                    existingNode = definitions.get(j);
                                }
                            }
                            // The FieldDefinition doesn't exist so add it
                            if (!exists) {
                                definitions.add(newNodeObj.getFieldDefinitions().get(i));

                            }
                            else {
                                // If we got here, that means that an existing FieldDefinition exists so we need to create a new one and merge the InputValueDefinitions
                                List<InputValueDefinition> existingInputs = existingNode.getInputValueDefinitions();
                                List<InputValueDefinition> newInputs = new ArrayList<>(newNodeObj.getFieldDefinitions().get(i).getInputValueDefinitions());

                                // Add missing inputs from existingInputs to newInputs.
                                Boolean ivdExists = false;
                                for (InputValueDefinition existing : existingInputs) {

                                    for (InputValueDefinition input : newInputs) {
                                        if (input.getName().equalsIgnoreCase(existing.getName())) {
                                            ivdExists = true;
                                        }
                                    }
                                    if (!ivdExists) {
                                        newInputs.add(existing);
                                    }
                                }

                                // Build a new FieldDefinition with the merged inputs: newInputs
                                FieldDefinition fd = FieldDefinition.newFieldDefinition()
                                        .name(existingNode.getName())
                                        .type(existingNode.getType())
                                        .inputValueDefinitions(newInputs)
                                        .build();

                                // Replace the existing definition
                                definitions.remove(existingNode);
                                definitions.add(fd);
                            }
                        }

                        ObjectTypeDefinition changedNode = node.transform(builder -> {
                            builder.fieldDefinitions(definitions);
                        });

                        return changeNode(context, changedNode);
                    }
                }
                return changeNode(context, node);
            }

            public TraversalControl visitEnumTypeDefinition(EnumTypeDefinition node, TraverserContext<Node> context) {
                // We update definitions in an existing EnumTypeDefinition
                // Only work with the current newNode if it's an EnumTypeDefinition
                if (newNode instanceof EnumTypeDefinition) {
                    EnumTypeDefinition newNodeObj = (EnumTypeDefinition) newNode;

                    // Only continue if the current node has the same name as newNode
                    if (node.getName().equalsIgnoreCase(newNodeObj.getName())) {
                        ArrayList<EnumValueDefinition> definitions = new ArrayList<>(node.getEnumValueDefinitions());

                        // Only add fields from newNode if it doesn't already exist within the current node
                        for (int i = 0; i < newNodeObj.getEnumValueDefinitions().size(); i++) {
                            Boolean exists = false;
                            for (int j = 0; j < definitions.size(); j++) {
                                if (newNodeObj.getEnumValueDefinitions().get(i).getName().equalsIgnoreCase(definitions.get(j).getName())) {
                                    exists = true;
                                }
                            }
                            if (!exists) {
                                definitions.add(newNodeObj.getEnumValueDefinitions().get(i));
                            }
                        }

                        EnumTypeDefinition changedNode = node.transform(builder -> {
                            builder.enumValueDefinitions(definitions);
                        });

                        return changeNode(context, changedNode);
                    }
                }
                return changeNode(context, node);
            }

            public TraversalControl visitUnionTypeDefinition(UnionTypeDefinition node, TraverserContext<Node> context) {
                // We update definitions in an existing UnionTypeDefinition
                // Only work with the current newNode if it's an UnionTypeDefinition
                if (newNode instanceof UnionTypeDefinition) {
                    UnionTypeDefinition newNodeObj = (UnionTypeDefinition) newNode;

                    // Only continue if the current node has the same name as newNode
                    if (node.getName().equalsIgnoreCase(newNodeObj.getName())) {
                        ArrayList<Type> memberTypes = new ArrayList<>(node.getMemberTypes());

                        // Only add fields from newNode if it doesn't already exist within the current node
                        Boolean exists = false;
                        for (int i = 0; i < newNodeObj.getMemberTypes().size(); i++) {
                            for (int j = 0; j < memberTypes.size(); j++) {
                                if (newNodeObj.getMemberTypes().get(i).toString().equalsIgnoreCase(memberTypes.get(j).toString())) {
                                    exists = true;
                                }
                            }
                            if (!exists) {
                                memberTypes.add(newNodeObj.getMemberTypes().get(i));
                            }
                        }

                        UnionTypeDefinition changedNode = UnionTypeDefinition.newUnionTypeDefinition()
                                .name(node.getName())
                                .memberTypes(memberTypes)
                                .build();
                        return changeNode(context, changedNode);
                    }
                }
                return changeNode(context, node);
            }

            public TraversalControl visitInputObjectTypeDefinition(InputObjectTypeDefinition node, TraverserContext<Node> context) {
                // We update definitions in an existing InputObjectTypeDefinition
                // Only work with the current newNode if it's an InputObjectTypeDefinition
                if (newNode instanceof InputObjectTypeDefinition) {
                    InputObjectTypeDefinition newNodeObj = (InputObjectTypeDefinition) newNode;

                    // Only continue if the current node has the same name as newNode
                    if (node.getName().equalsIgnoreCase(newNodeObj.getName())) {
                        ArrayList<InputValueDefinition> definitions = new ArrayList<>(node.getInputValueDefinitions());

                        // Only add fields from newNode if it doesn't already exist within the current node
                        for (int i = 0; i < newNodeObj.getInputValueDefinitions().size(); i++) {
                            Boolean exists = false;
                            for (int j = 0; j < definitions.size(); j++) {
                                if (newNodeObj.getInputValueDefinitions().get(i).getName().equalsIgnoreCase(definitions.get(j).getName())) {
                                    exists = true;
                                }
                            }
                            if (!exists) {
                                definitions.add(newNodeObj.getInputValueDefinitions().get(i));
                            }
                        }

                        InputObjectTypeDefinition changedNode = node.transform(builder -> {
                            builder.inputValueDefinitions(definitions);
                        });

                        return changeNode(context, changedNode);
                    }
                }
                return changeNode(context, node);
            }


            public TraversalControl visitDocument(Document node, TraverserContext<Node> context) {

                // Only work with the current newNode if it's an ObjectTypeDefinition
                if (newNode instanceof ObjectTypeDefinition) {
                    ObjectTypeDefinition newNodeObj = (ObjectTypeDefinition) newNode;
                    ArrayList<Definition> definitions = new ArrayList<>(node.getDefinitions());

                    // Don't modify the Document if the ObjectTypeDefinition already exists
                    // We update fields within an existing object with visitObjectTypeDefinition()
                    for (Definition definition : definitions) {
                        if (definition instanceof ObjectTypeDefinition) {
                            ObjectTypeDefinition current = (ObjectTypeDefinition) definition;

                            if (current.getName().equalsIgnoreCase(newNodeObj.getName())) {
                                return changeNode(context, node);
                            }
                        }
                    }

                    // If we made it here, newNode doesn't exist, so we add it to the Document
                    definitions.add(newNodeObj);

                    Document changedNode = node.transform(builder -> {
                        builder.definitions(definitions);
                    });

                    return changeNode(context, changedNode);
                }

                // Only work with the current newNode if it's an InputObjectTypeDefinition
                else if (newNode instanceof InputObjectTypeDefinition) {
                    InputObjectTypeDefinition newNodeObj = (InputObjectTypeDefinition) newNode;
                    ArrayList<Definition> definitions = new ArrayList<>(node.getDefinitions());

                    // Don't modify the Document if the InputObjectTypeDefinition already exists
                    // We update fields within an existing object with InputObjectTypeDefinition()
                    for (Definition definition : definitions) {
                        if (definition instanceof InputObjectTypeDefinition) {
                            InputObjectTypeDefinition current = (InputObjectTypeDefinition) definition;

                            if (current.getName().equalsIgnoreCase(newNodeObj.getName())) {
                                return changeNode(context, node);
                            }
                        }
                    }

                    // If we made it here, newNode doesn't exist, so we add it to the Document
                    definitions.add(newNodeObj);

                    Document changedNode = node.transform(builder -> {
                        builder.definitions(definitions);
                    });

                    return changeNode(context, changedNode);
                }

                // Only work with the current newNode if it's an UnionTypeDefinition
                else if (newNode instanceof UnionTypeDefinition) {
                    UnionTypeDefinition newNodeObj = (UnionTypeDefinition) newNode;
                    ArrayList<Definition> definitions = new ArrayList<>(node.getDefinitions());

                    // Don't modify the Document if the UnionTypeDefinition already exists
                    // We update fields within an existing object with visitUnionTypeDefinition()
                    for (Definition definition : definitions) {
                        if (definition instanceof UnionTypeDefinition) {
                            UnionTypeDefinition current = (UnionTypeDefinition) definition;

                            if (current.getName().equalsIgnoreCase(newNodeObj.getName())) {
                                return changeNode(context, node);
                            }
                        }
                    }

                    // If we made it here, newNode doesn't exist, so we add it to the Document
                    definitions.add(newNodeObj);

                    Document changedNode = node.transform(builder -> {
                        builder.definitions(definitions);
                    });

                    return changeNode(context, changedNode);
                }

                // Only work with the current newNode if it's an EnumTypeDefinition
                else if (newNode instanceof EnumTypeDefinition) {
                    EnumTypeDefinition newNodeObj = (EnumTypeDefinition) newNode;
                    ArrayList<Definition> definitions = new ArrayList<>(node.getDefinitions());

                    // Don't modify the Document if the EnumTypeDefinition already exists
                    // We update fields within an existing object with EnumTypeDefinition()
                    for (Definition definition : definitions) {
                        if (definition instanceof EnumTypeDefinition) {
                            EnumTypeDefinition current = (EnumTypeDefinition) definition;

                            if (current.getName().equalsIgnoreCase(newNodeObj.getName())) {
                                return changeNode(context, node);
                            }
                        }
                    }

                    // If we made it here, newNode doesn't exist, so we add it to the Document
                    definitions.add(newNodeObj);

                    Document changedNode = node.transform(builder -> {
                        builder.definitions(definitions);
                    });

                    return changeNode(context, changedNode);
                }

                // Only work with the current newNode if it's an EnumTypeDefinition
                else if (newNode instanceof EnumTypeDefinition) {
                    EnumTypeDefinition newNodeObj = (EnumTypeDefinition) newNode;
                    ArrayList<Definition> definitions = new ArrayList<>(node.getDefinitions());

                    // Don't modify the Document if the EnumTypeDefinition already exists
                    // We update fields within an existing object with EnumTypeDefinition()
                    for (Definition definition : definitions) {
                        if (definition instanceof EnumTypeDefinition) {
                            EnumTypeDefinition current = (EnumTypeDefinition) definition;

                            if (current.getName().equalsIgnoreCase(newNodeObj.getName())) {
                                return changeNode(context, node);
                            }
                        }
                    }
                    // If we made it here, newNode doesn't exist, so we add it to the Document
                    definitions.add(newNodeObj);

                    Document changedNode = node.transform(builder -> {
                        builder.definitions(definitions);
                    });

                    return changeNode(context, changedNode);
                }
                return changeNode(context, node);
            }
        };

        AstTransformer transformer = new AstTransformer();
        return (Document) transformer.transform(schema, visitor);
    }
}