package burp;

import javax.swing.*;
import java.awt.*;
import net.miginfocom.swing.MigLayout;
import java.awt.event.ActionListener;

public class SuiteTab extends JPanel {

    JButton graphiqlGenerateButton;
    JButton voyagerGenerateButton;
    JButton graphiqlSetCustomButton;
    JButton voyagerSetCustomButton;

    JButton customHeadersButton;
    JButton resetCustomHeadersButton;

    JButton targetUrlSetButton;

    JButton introspectionResetButton;
    JButton schemaMergeButton;
    JButton schemaCopySdlButton;
    JButton schemaCopyJsonButton;
    JButton clearIntrospectionErrorsButton;

    JLabel graphiqlIdentifierLabel;
    JLabel voyagerIdentifierLabel;
    JLabel leftPanelLabel;
    JLabel rightPanelLabel;
    JLabel schemaTipLabel;
    JLabel introspectionErrorLabel;
    JLabel schemaSourceLabel;
    JLabel targetUrlLabel;
    JLabel customHeadersLabel;

    JTextField graphiqlIdentifierTextField;
    JTextField voyagerIdentifierTextField;
    JTextField targetUrlTextField;

    JTextField customHeadersTextField;

    JTextArea schemaTextArea;
    JScrollPane schemaScrollPane;
    JTextArea introspectionErrorTextArea;
    JScrollPane introspectionErrorScrollPane;

    JCheckBox graphiqlEmuCheckBox;
    JCheckBox voyagerEmuCheckBox;
    JCheckBox introspectionEmuCheckBox;

    ButtonGroup introspectionRadioButtonGroup;
    JRadioButton introspectionProxyRadioButton;
    JRadioButton introspectionFileRadioButton;

    JPanel leftPanel;
    JPanel rightPanel;

    Font heading;

    public SuiteTab() {

        // Fonts
        heading = new Font("", Font.BOLD, 16);

        // Buttons
        graphiqlGenerateButton = new JButton("Generate");
        graphiqlGenerateButton.setActionCommand("generateGraphiqlIdentifier");

        voyagerGenerateButton = new JButton("Generate");
        voyagerGenerateButton.setActionCommand("generateVoyagerIdentifier");

        graphiqlSetCustomButton = new JButton("Set");
        graphiqlSetCustomButton.setActionCommand("setCustomGraphiqlIdentifier");

        voyagerSetCustomButton = new JButton("Set");
        voyagerSetCustomButton.setActionCommand("setCustomVoyagerIdentifier");

        introspectionResetButton = new JButton("Reset Schema");
        introspectionResetButton.setActionCommand("resetIntrospection");

        schemaMergeButton = new JButton("Replace Schema");
        schemaMergeButton.setActionCommand("replaceSchema");

        clearIntrospectionErrorsButton = new JButton("Clear Errors");
        clearIntrospectionErrorsButton.setActionCommand("clearIntrospectionErrors");

        schemaCopySdlButton = new JButton("Copy Schema (SDL)");
        schemaCopySdlButton.setActionCommand("copySdlSchema");

        schemaCopyJsonButton = new JButton("Copy Schema (JSON)");
        schemaCopyJsonButton.setActionCommand("copyJsonSchema");

        targetUrlSetButton = new JButton("Set Target URL");
        targetUrlSetButton.setActionCommand("setTargetUrl");

        customHeadersButton = new JButton("Set");
        customHeadersButton.setActionCommand("replaceCustomHeaders");

        resetCustomHeadersButton = new JButton("Reset");
        resetCustomHeadersButton.setActionCommand("resetCustomHeaders");


        // Labels
        graphiqlIdentifierLabel = new JLabel();
        graphiqlIdentifierLabel.setText("GraphiQL Identifier");

        voyagerIdentifierLabel = new JLabel();
        voyagerIdentifierLabel.setText("Voyager Identifier");

        leftPanelLabel = new JLabel("Emulator Settings");
        leftPanelLabel.setHorizontalTextPosition(SwingConstants.CENTER);
        leftPanelLabel.setFont(heading);

        rightPanelLabel= new JLabel("Introspection Settings", SwingConstants.CENTER);
        rightPanelLabel.setFont(heading);

        schemaSourceLabel = new JLabel("Schema Source");
        schemaTipLabel = new JLabel("Paste a schema as either JSON or SDL format.");

        introspectionErrorLabel = new JLabel("Error");

        targetUrlLabel = new JLabel();
        targetUrlLabel.setText("GraphQL Endpoint (https://example.com/graphql)");

        customHeadersLabel = new JLabel();
        customHeadersLabel.setText("Custom Headers (Custom1: value1\\nCustom2: value2)");

        // Text fields
        graphiqlIdentifierTextField = new JTextField();
        graphiqlIdentifierTextField.setColumns(24);

        voyagerIdentifierTextField = new JTextField();
        voyagerIdentifierTextField.setColumns(24);

        targetUrlTextField = new JTextField();
        targetUrlTextField.setColumns(12);

        customHeadersTextField = new JTextField();
        customHeadersTextField.setColumns(24);

        // Text panes
        schemaTextArea = new JTextArea(8, 50);
        schemaTextArea.setEditable(true);
        schemaScrollPane = new JScrollPane(schemaTextArea);

        introspectionErrorTextArea = new JTextArea(8, 50);
        introspectionErrorTextArea.setEditable(false);
        introspectionErrorScrollPane = new JScrollPane(introspectionErrorTextArea);


        // Checkboxes
        graphiqlEmuCheckBox = new JCheckBox("GraphiQL Emulation");
        graphiqlEmuCheckBox.setActionCommand("toggleGraphiqlEmu");

        voyagerEmuCheckBox = new JCheckBox("Voyager Emulation");
        voyagerEmuCheckBox.setActionCommand("toggleVoyagerEmu");

        introspectionEmuCheckBox = new JCheckBox("Introspection Emulation");
        introspectionEmuCheckBox.setActionCommand("toggleIntrospectionEmu");

        // radio buttons
        introspectionRadioButtonGroup = new ButtonGroup();

        // default option
        introspectionFileRadioButton = new JRadioButton();
        introspectionFileRadioButton.setSelected(true);
        introspectionFileRadioButton.setText("File");
        introspectionFileRadioButton.setActionCommand("selectSourceFile");

        introspectionProxyRadioButton = new JRadioButton();
        introspectionProxyRadioButton.setText("Proxy");
        introspectionProxyRadioButton.setActionCommand("selectSourceProxy");

        introspectionRadioButtonGroup.add(introspectionFileRadioButton);
        introspectionRadioButtonGroup.add(introspectionProxyRadioButton);

        // Set up main panel
        setLayout(new MigLayout("wrap 2", "center", "top"));

        // Left panel
        leftPanel = new JPanel(new MigLayout("wrap 3"));
        add(leftPanel);

        leftPanel.add(leftPanelLabel, "span 3");
        leftPanel.add(graphiqlIdentifierLabel);
        leftPanel.add(graphiqlIdentifierTextField);
        leftPanel.add(graphiqlSetCustomButton, "split 2");
        leftPanel.add(graphiqlGenerateButton, "wrap");

        leftPanel.add(voyagerIdentifierLabel);
        leftPanel.add(voyagerIdentifierTextField);
        leftPanel.add(voyagerSetCustomButton, "split 2");
        leftPanel.add(voyagerGenerateButton, "wrap");

        leftPanel.add(customHeadersLabel);

        leftPanel.add(customHeadersTextField);
        leftPanel.add(customHeadersButton, "split 2");
        leftPanel.add(resetCustomHeadersButton, "wrap");

        leftPanel.add(graphiqlEmuCheckBox, "split 2");
        leftPanel.add(voyagerEmuCheckBox);
        leftPanel.add(introspectionEmuCheckBox);

        // Right panel
        rightPanel = new JPanel(new MigLayout("wrap 5"));
        add(rightPanel);
        rightPanel.add(rightPanelLabel, "wrap");
        rightPanel.add(schemaSourceLabel, "split 3");
        rightPanel.add(introspectionFileRadioButton);
        rightPanel.add(introspectionProxyRadioButton, "wrap");

        rightPanel.add(targetUrlLabel, "split 3");
        rightPanel.add(targetUrlTextField);
        rightPanel.add(targetUrlSetButton, "wrap");

        rightPanel.add(schemaTipLabel, "wrap");
        rightPanel.add(schemaScrollPane, "wrap");
        rightPanel.add(introspectionErrorLabel, "wrap");
        rightPanel.add(introspectionErrorScrollPane, "wrap");
        rightPanel.add(schemaMergeButton, "split 5");
        rightPanel.add(schemaCopySdlButton);
        rightPanel.add(schemaCopyJsonButton);
        rightPanel.add(clearIntrospectionErrorsButton);
        rightPanel.add(introspectionResetButton);
    }

    public void enableRightPanel(Boolean state, Boolean setSourceProxy) {
        for (Component component : rightPanel.getComponents()) {
            component.setEnabled(state);
        }

        if (setSourceProxy) {
            setSchemaFile(false);
        }

        else {
            setSchemaProxy(false);
        }
    }

    public void setSchemaFile(Boolean state) {
        schemaTipLabel.setEnabled(state);
        schemaTextArea.setEnabled(state);
        schemaMergeButton.setEnabled(state);
        schemaTipLabel.setEnabled(state);
        introspectionErrorLabel.setEnabled(state);
    }

    public void setSchemaProxy(Boolean state) {
        targetUrlTextField.setEnabled(state);
        targetUrlLabel.setEnabled(state);
        targetUrlSetButton.setEnabled(state);
    }

    public void setupListeners(ActionListener actionListener) {
        graphiqlGenerateButton.addActionListener(actionListener);
        voyagerGenerateButton.addActionListener(actionListener);
        graphiqlSetCustomButton.addActionListener(actionListener);
        voyagerSetCustomButton.addActionListener(actionListener);
        introspectionEmuCheckBox.addActionListener(actionListener);
        voyagerEmuCheckBox.addActionListener(actionListener);
        graphiqlEmuCheckBox.addActionListener(actionListener);
        schemaMergeButton.addActionListener(actionListener);
        introspectionResetButton.addActionListener(actionListener);
        clearIntrospectionErrorsButton.addActionListener(actionListener);
        schemaCopyJsonButton.addActionListener(actionListener);
        schemaCopySdlButton.addActionListener(actionListener);
        targetUrlSetButton.addActionListener(actionListener);
        introspectionFileRadioButton.addActionListener(actionListener);
        introspectionProxyRadioButton.addActionListener(actionListener);
        customHeadersButton.addActionListener(actionListener);
        resetCustomHeadersButton.addActionListener(actionListener);
    }

    public static void main(String[] args) {
        SuiteTab suiteTab = new SuiteTab();
        JFrame main = new JFrame("GraphQuail");
        main.setVisible(true);
        main.add(suiteTab);
    }
}