package com.zzg.mybatis.generator.controller;

import java.io.File;
import java.net.URL;
import java.sql.SQLTimeoutException;
import java.util.*;

import com.mysql.jdbc.exceptions.jdbc4.CommunicationsException;
import com.zzg.mybatis.generator.bridge.MybatisGeneratorBridge;
import com.zzg.mybatis.generator.model.*;
import com.zzg.mybatis.generator.util.DbUtil;
import com.zzg.mybatis.generator.util.StringUtils;
import com.zzg.mybatis.generator.util.XMLConfigHelper;
import com.zzg.mybatis.generator.view.AlertUtil;
import com.zzg.mybatis.generator.view.LeftDbTreeCell;
import com.zzg.mybatis.generator.view.UIProgressCallback;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.*;
import javafx.util.Callback;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.api.ProgressCallback;
import org.mybatis.generator.api.ShellCallback;
import org.mybatis.generator.api.VerboseProgressCallback;
import org.mybatis.generator.config.*;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser.ExtensionFilter;

public class MainUIController extends BaseFXController {

    private static final Logger _LOG = LoggerFactory.getLogger(MainUIController.class);

    // tool bar buttons
    @FXML
    private Label connectionLabel;
    @FXML
    private TextField connectorPathField;
    @FXML
    private TextField modelTargetPackage;
    @FXML
    private TextField mapperTargetPackage;
    @FXML
    private TextField daoTargetPackage;
    @FXML
    private TextField tableNameField;
    @FXML
    private TextField domainObjectNameField;
    @FXML
    private TextField modelTargetProject;
    @FXML
    private TextField mappingTargetProject;
    @FXML
    private TextField daoTargetProject;
    @FXML
    private TextField projectFolderField;
    @FXML
    private CheckBox offsetLimitCheckBox;
    @FXML
    private CheckBox commentCheckBox;

    @FXML
    private TreeView<String> leftDBTree;
    @FXML
    private TextArea consoleTextArea;
    // Current selected databaseConfig
    private DatabaseConfig selectedDatabaseConfig;
    // Current selected tableName
    private String tableName;

    private List<IgnoredColumn> ignoredColumns;

    private List<ColumnOverride> columnOverrides;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ImageView dbImage = new ImageView("icons/computer.png");
        dbImage.setFitHeight(40);
        dbImage.setFitWidth(40);
        connectionLabel.setGraphic(dbImage);
        connectionLabel.setOnMouseClicked(event -> {
            NewConnectionController controller = (NewConnectionController) loadFXMLPage("New Connection", FXMLPage.NEW_CONNECTION);
            controller.setMainUIController(this);
        });

        leftDBTree.setShowRoot(false);
        leftDBTree.setRoot(new TreeItem<>());
        Callback<TreeView<String>, TreeCell<String>> defaultCellFactory = TextFieldTreeCell.forTreeView();
        leftDBTree.setCellFactory((TreeView<String> tv) -> {
            TreeCell<String> cell = defaultCellFactory.call(tv);
            cell.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                int level = leftDBTree.getTreeItemLevel(cell.getTreeItem());
                TreeCell<String> treeCell = (TreeCell<String>) event.getSource();
                TreeItem<String> treeItem = treeCell.getTreeItem();
                if (level == 1) {
                    final ContextMenu contextMenu = new ContextMenu();
                    MenuItem item1 = new MenuItem("Close Connection");
                    item1.setOnAction(event1 -> {
                        treeItem.getChildren().clear();
                    });
                    MenuItem item2 = new MenuItem("Delete");
                    item2.setOnAction(event1 -> {
                        // TODO
                    });
                    contextMenu.getItems().addAll(item1);
                    cell.setContextMenu(contextMenu);
                }
                if (event.getClickCount() == 2) {
                    treeItem.setExpanded(true);
                    if (level == 1) {
                        System.out.println("index: " + leftDBTree.getSelectionModel().getSelectedIndex());
                        DatabaseConfig selectedConfig = (DatabaseConfig) treeItem.getGraphic().getUserData();
                        try {
                            List<String> tables = DbUtil.getTableNames(selectedConfig);
                            if (tables != null && tables.size() > 0) {
                                ObservableList<TreeItem<String>> children = cell.getTreeItem().getChildren();
                                children.clear();
                                for (String tableName : tables) {
                                    TreeItem<String> newTreeItem = new TreeItem<>();
                                    ImageView imageView = new ImageView("icons/table.png");
                                    imageView.setFitHeight(16);
                                    imageView.setFitWidth(16);
                                    newTreeItem.setGraphic(imageView);
                                    newTreeItem.setValue(tableName);
                                    children.add(newTreeItem);
                                }
                            }
                        } catch (CommunicationsException e) {
                            _LOG.error(e.getMessage(), e);
                            AlertUtil.showErrorAlert("Connection timeout");
                        } catch (Exception e) {
                            _LOG.error(e.getMessage(), e);
                            AlertUtil.showErrorAlert(e.getMessage());
                        }
                    } else if (level == 2) { // left DB tree level3
                        String tableName = treeCell.getTreeItem().getValue();
                        selectedDatabaseConfig = (DatabaseConfig) treeItem.getParent().getGraphic().getUserData();
                        this.tableName = tableName;
                        tableNameField.setText(tableName);
                        domainObjectNameField.setText(StringUtils.dbStringToCamelStyle(tableName));
                    }
                }
            });
            return cell;
        });
        loadLeftDBTree();
    }

    void loadLeftDBTree() {
        TreeItem rootTreeItem = leftDBTree.getRoot();
        rootTreeItem.getChildren().clear();
        List<DatabaseConfig> dbConfigs = null;
        try {
            dbConfigs = XMLConfigHelper.loadDatabaseConfig();
            for (DatabaseConfig dbConfig : dbConfigs) {
                TreeItem<String> treeItem = new TreeItem<>();
                treeItem.setValue(dbConfig.getName());
                ImageView dbImage = new ImageView("icons/computer.png");
                dbImage.setFitHeight(16);
                dbImage.setFitWidth(16);
                dbImage.setUserData(dbConfig);
                treeItem.setGraphic(dbImage);
                rootTreeItem.getChildren().add(treeItem);
            }
        } catch (Exception e) {
            _LOG.error("connect db failed, reason: {}", e);
            AlertUtil.showErrorAlert(e.getMessage() + "\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    @FXML
    public void chooseConnectorFile() {
        FileChooser directoryChooser = new FileChooser();
        File selectedFolder = directoryChooser.showOpenDialog(getPrimaryStage());
        if (selectedFolder != null) {
            connectorPathField.setText(selectedFolder.getAbsolutePath());
        }
    }

    @FXML
    public void chooseProjectFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File selectedFolder = directoryChooser.showDialog(getPrimaryStage());
        if (selectedFolder != null) {
            projectFolderField.setText(selectedFolder.getAbsolutePath());
        }
    }

    @FXML
    public void generateCode() {
        if (tableName == null) {
            AlertUtil.showErrorAlert("Please select table from left DB treee first");
            return;
        }
        GeneratorConfig generatorConfig = getGeneratorConfigFromUI();
        MybatisGeneratorBridge bridge = new MybatisGeneratorBridge();
        bridge.setGeneratorConfig(generatorConfig);
        bridge.setDatabaseConfig(selectedDatabaseConfig);
        bridge.setIgnoredColumns(ignoredColumns);
        bridge.setColumnOverrides(columnOverrides);
        bridge.setProgressCallback(new UIProgressCallback(consoleTextArea));
        try {
            bridge.generate();
        } catch (Exception e) {
            AlertUtil.showErrorAlert(e.getMessage());
        }
    }

    public GeneratorConfig getGeneratorConfigFromUI() {
        GeneratorConfig generatorConfig = new GeneratorConfig();
        generatorConfig.setConnectorJarPath(connectorPathField.getText());
        generatorConfig.setProjectFolder(projectFolderField.getText());
        generatorConfig.setModelPackage(modelTargetPackage.getText());
        generatorConfig.setModelPackageTargetFolder(modelTargetProject.getText());
        generatorConfig.setDaoPackage(daoTargetPackage.getText());
        generatorConfig.setDaoTargetFolder(daoTargetProject.getText());
        generatorConfig.setMappingXMLPackage(mapperTargetPackage.getText());
        generatorConfig.setMappingXMLTargetFolder(mappingTargetProject.getText());
        generatorConfig.setTableName(tableNameField.getText());
        generatorConfig.setDomainObjectName(domainObjectNameField.getText());
        generatorConfig.setOffsetLimit(offsetLimitCheckBox.isSelected());
        generatorConfig.setComment(commentCheckBox.isSelected());
        return generatorConfig;
    }

    public void setGeneratorConfigIntoUI(GeneratorConfig generatorConfig) {
        connectorPathField.setText(generatorConfig.getConnectorJarPath());
        projectFolderField.setText(generatorConfig.getProjectFolder());
        modelTargetPackage.setText(generatorConfig.getModelPackage());
        modelTargetProject.setText(generatorConfig.getModelPackageTargetFolder());
        daoTargetPackage.setText(generatorConfig.getDaoPackage());
        daoTargetProject.setText(generatorConfig.getDaoTargetFolder());
        mapperTargetPackage.setText(generatorConfig.getMappingXMLPackage());
        mappingTargetProject.setText(generatorConfig.getMappingXMLTargetFolder());
    }

    @FXML
    public void openTableColumnCustomizationPage() {
        if (tableName == null) {
            AlertUtil.showErrorAlert("Please select table from left DB treee first");
            return;
        }
        SelectTableColumnController controller = (SelectTableColumnController) loadFXMLPage("Select Columns", FXMLPage.SELECT_TABLE_COLUMN);
        controller.setMainUIController(this);
        try {
            // If select same schema and another table, update table data
            if (!tableName.equals(controller.getTableName())) {
                List<UITableColumnVO> tableColumns = DbUtil.getTableColumns(selectedDatabaseConfig, tableName);
                controller.setColumnList(FXCollections.observableList(tableColumns));
                controller.setTableName(tableName);
            }
            controller.showDialogStage();
        } catch (Exception e) {
            _LOG.error(e.getMessage(), e);
            AlertUtil.showErrorAlert(e.getMessage());
        }
    }

    public void setIgnoredColumns(List<IgnoredColumn> ignoredColumns) {
        this.ignoredColumns = ignoredColumns;
    }

    public void setColumnOverrides(List<ColumnOverride> columnOverrides) {
        this.columnOverrides = columnOverrides;
    }
}
