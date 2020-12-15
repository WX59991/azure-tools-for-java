/*
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.microsoft.intellij.helpers.mysql;

import com.microsoft.azure.management.mysql.v2020_01_01.Server;
import com.microsoft.azure.management.mysql.v2020_01_01.ServerState;
import com.microsoft.azure.management.mysql.v2020_01_01.implementation.DatabaseInner;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.toolkit.intellij.common.AzureHideableTitledSeparator;
import com.microsoft.azure.toolkit.intellij.mysql.ConnectionSecurityPanel;
import com.microsoft.azure.toolkit.intellij.mysql.ConnectionStringsOutput;
import com.microsoft.azure.toolkit.intellij.mysql.DatabaseComboBox;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azuretools.azurecommons.util.Utils;
import com.microsoft.azuretools.core.mvp.model.AzureMvpModel;
import com.microsoft.azuretools.core.mvp.model.mysql.MySQLMvpModel;
import com.microsoft.intellij.helpers.base.BaseEditor;
import com.microsoft.tooling.msservices.serviceexplorer.azure.mysql.MySQLProperty;
import com.microsoft.tooling.msservices.serviceexplorer.azure.mysql.MySQLPropertyMvpView;
import com.microsoft.tooling.msservices.serviceexplorer.azure.mysql.MySQLPropertyViewPresenter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;

public class MySQLPropertyView extends BaseEditor implements MySQLPropertyMvpView {

    public static final String ID = "com.microsoft.intellij.helpers.mysql.AzureMySQLPropertyView";
    private final MySQLPropertyViewPresenter<MySQLPropertyView> propertyViewPresenter;

    private AzureHideableTitledSeparator overviewSeparator;
    private MySQLPropertryOverview overview;
    private AzureHideableTitledSeparator connectionSecuritySeparator;
    private ConnectionSecurityPanel connectionSecurity;
    private AzureHideableTitledSeparator connectionStringsSeparator;
    private ConnectionStringsOutput connectionStringsJDBC;
    private ConnectionStringsOutput connectionStringsSpring;
    private JPanel rootPanel;
    private JPanel contextPanel;
    private JScrollPane scrollPane;
    private MySQLPropertyAction propertyActionPanel;
    private DatabaseComboBox databaseComboBox;
    private JLabel databaseLabel;
    public static final String MYSQL_OUTPUT_TEXT_PATTERN_SPRING =
            "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver" + StringUtils.LF +
                    "spring.datasource.url=jdbc:mysql://localhost:3306/%s?useSSL=true&requireSSL=false" + StringUtils.LF +
                    "spring.datasource.username=qianjin@qianjin-mysql-01" + StringUtils.LF + "spring.datasource.password={your_password}";

    public static final String MYSQL_OUTPUT_TEXT_PATTERN_JDBC =
            "String url =\"jdbc:mysql://qianjin-mysql-10.mysql.database.azure.com:3306/%s?useSSL=true&requireSSL=false\";" + StringUtils.LF +
                    "myDbConn = DriverManager.getConnection(url, \"%s\", {your_password});";

    private MySQLProperty property;

    private Boolean originalAllowAccessToAzureServices;
    private Boolean originalAllowAccessToLocal;

    MySQLPropertyView() {
        super();
        overviewSeparator.addContentComponent(overview);
        connectionSecuritySeparator.addContentComponent(connectionSecurity);
        connectionStringsSeparator.addContentComponent(databaseLabel);
        connectionStringsSeparator.addContentComponent(databaseComboBox);
        connectionStringsSeparator.addContentComponent(connectionStringsJDBC);
        connectionStringsSeparator.addContentComponent(connectionStringsSpring);
        connectionStringsJDBC.getTitleLabel().setText("JDBC");
        connectionStringsJDBC.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_JDBC, null, null));
        connectionStringsSpring.getTitleLabel().setText("Spring");
        connectionStringsSpring.getOutputTextArea().setText(getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_SPRING, null, null));
        this.propertyViewPresenter = new MySQLPropertyViewPresenter<>();
        this.propertyViewPresenter.onAttachView(this);
        init();
        initListeners();
    }

    private String getConnectionString(final String pattern, final String database, final String username) {
        if (StringUtils.isBlank(database)) {
            return String.format(pattern, "{your_database}", "{your_username}");
        } else {
            return String.format(pattern, database, username);
        }
    }

    private void init() {
        originalAllowAccessToAzureServices = connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected();
        originalAllowAccessToLocal = connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
    }

    private void initListeners() {
        // update to trigger save/discard buttons
        connectionSecurity.getAllowAccessFromAzureServicesCheckBox().addItemListener(this::onCheckBoxChanged);
        connectionSecurity.getAllowAccessFromLocalMachineCheckBox().addItemListener(this::onCheckBoxChanged);
        // actions of copy buttons
        connectionStringsJDBC.getCopyButton().addActionListener(this::onJDBCCopyButtonClicked);
        connectionStringsSpring.getCopyButton().addActionListener(this::onSpringCopyButtonClicked);
        // save/discard buttons
        propertyActionPanel.getSaveButton().addActionListener(this::onSaveButtonClicked);
        propertyActionPanel.getDiscardButton().addActionListener(this::onDiscardButtonClicked);
        // database combox changed
        databaseComboBox.addItemListener(this::onDatabaseComboBoxChanged);
    }

    private void onCheckBoxChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED || itemEvent.getStateChange() == ItemEvent.DESELECTED) {
            System.out.println("check box changed...");
            Boolean changed = MySQLPropertyView.this.changed();
            MySQLPropertyView.this.propertyActionPanel.getSaveButton().setEnabled(changed);
            MySQLPropertyView.this.propertyActionPanel.getDiscardButton().setEnabled(changed);
        }
    }

    private void onJDBCCopyButtonClicked(ActionEvent e) {
        try {
            Utils.copyToSystemClipboard(MySQLPropertyView.this.connectionStringsJDBC.getOutputTextArea().getText());
        } catch (Exception exception) {
        }
    }

    private void onSpringCopyButtonClicked(ActionEvent e) {
        try {
            Utils.copyToSystemClipboard(MySQLPropertyView.this.connectionStringsSpring.getOutputTextArea().getText());
        } catch (Exception exception) {
        }
    }

    private void onSaveButtonClicked(ActionEvent e) {
        final String actionName = "Saving";
        String originalText = MySQLPropertyView.this.propertyActionPanel.getSaveButton().getText();
        MySQLPropertyView.this.propertyActionPanel.getSaveButton().setText(actionName);
        MySQLPropertyView.this.propertyActionPanel.getSaveButton().setEnabled(false);
        Runnable runnable = () -> {
            boolean allowAccessToAzureServices = connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected();
            if (!originalAllowAccessToAzureServices.equals(allowAccessToAzureServices)) {
                MySQLMvpModel.FirewallRuleMvpModel
                        .updateAllowAccessFromAzureServices(property.getSubscriptionId(), property.getServer(), allowAccessToAzureServices);
                originalAllowAccessToAzureServices = allowAccessToAzureServices;
            }
            boolean allowAccessToLocal = connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
            if (!originalAllowAccessToLocal.equals(allowAccessToLocal)) {
                MySQLMvpModel.FirewallRuleMvpModel.updateAllowAccessToLocalMachine(property.getSubscriptionId(), property.getServer(), allowAccessToLocal);
                originalAllowAccessToLocal = allowAccessToLocal;
            }
            MySQLPropertyView.this.propertyActionPanel.getSaveButton().setText(originalText);
            Boolean changed = MySQLPropertyView.this.changed();
            MySQLPropertyView.this.propertyActionPanel.getSaveButton().setEnabled(changed);
            MySQLPropertyView.this.propertyActionPanel.getDiscardButton().setEnabled(changed);
        };
        AzureTaskManager.getInstance().runInBackground(new AzureTask(null, String.format("%s...", actionName), false, runnable));
    }

    private void onDiscardButtonClicked(ActionEvent e) {
        MySQLPropertyView.this.propertyActionPanel.getSaveButton().setEnabled(false);
        MySQLPropertyView.this.propertyActionPanel.getDiscardButton().setEnabled(false);
        connectionSecurity.getAllowAccessFromAzureServicesCheckBox().setSelected(originalAllowAccessToAzureServices);
        connectionSecurity.getAllowAccessFromLocalMachineCheckBox().setSelected(originalAllowAccessToLocal);
    }

    private void onDatabaseComboBoxChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof DatabaseInner) {
            final DatabaseInner database = (DatabaseInner) e.getItem();
            connectionStringsJDBC.getOutputTextArea().setText(
                    getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_JDBC, database.name(), overview.getServerAdminLoginNameTextField().getText()));
            connectionStringsSpring.getOutputTextArea().setText(
                    getConnectionString(MYSQL_OUTPUT_TEXT_PATTERN_SPRING, database.name(), overview.getServerAdminLoginNameTextField().getText()));
        }
    }

    private boolean changed() {
        return originalAllowAccessToAzureServices != connectionSecurity.getAllowAccessFromAzureServicesCheckBox().getModel().isSelected()
                || originalAllowAccessToLocal != connectionSecurity.getAllowAccessFromLocalMachineCheckBox().getModel().isSelected();
    }

    @Override
    public @NotNull JComponent getComponent() {
        return rootPanel;
    }

    @Override
    public @NotNull String getName() {
        return ID;
    }

    @Override
    public void dispose() {

    }

    @Override
    public void onReadProperty(String sid, String resourceGroup, String name) {
        propertyViewPresenter.onGetMySQLProperty(sid, resourceGroup, name);
    }

    @Override
    public void showProperty(MySQLProperty property) {
        this.property = property;
        Server server = property.getServer();
        final String sid = AzureMvpModel.getSegment(server.id(), "subscriptions");
        Subscription subscription = AzureMvpModel.getInstance().getSubscriptionById(sid);
        if (subscription != null) {
            overview.getSubscriptionTextField().setText(subscription.displayName());
            databaseComboBox.setSubscription(subscription);
            databaseComboBox.setServer(server);
        }
        overview.getResourceGroupTextField().setText(server.resourceGroupName());
        overview.getStatusTextField().setText(server.userVisibleState().toString());
        overview.getLocationTextField().setText(server.region().label());
        overview.getSubscriptionIDTextField().setText(sid);
        overview.getServerNameTextField().setText(server.fullyQualifiedDomainName());
        overview.getServerNameTextField().setCaretPosition(0);
        overview.getServerAdminLoginNameTextField().setText(server.administratorLogin() + "@" + server.name());
        overview.getServerAdminLoginNameTextField().setCaretPosition(0);
        overview.getMysqlVersionTextField().setText(server.version().toString());
        String skuTier = server.sku().tier().toString();
        int skuCapacity = server.sku().capacity();
        int storageGB = server.storageProfile().storageMB() / 1024;
        String performanceConfigurations = skuTier + ", " + skuCapacity + " vCore(s), " + storageGB + " GB";
        overview.getPerformanceConfigurationsTextField().setText(performanceConfigurations);
        overview.getSslEnforceStatusTextField().setText(server.sslEnforcement().name());
        if (ServerState.READY.equals(server.userVisibleState())) {
            originalAllowAccessToAzureServices = MySQLMvpModel.FirewallRuleMvpModel.isAllowAccessFromAzureServices(property.getFirewallRules());
            connectionSecurity.getAllowAccessFromAzureServicesCheckBox().setSelected(originalAllowAccessToAzureServices);
            originalAllowAccessToLocal = MySQLMvpModel.FirewallRuleMvpModel.isAllowAccessFromLocalMachine(property.getFirewallRules());
            connectionSecurity.getAllowAccessFromLocalMachineCheckBox().setSelected(originalAllowAccessToLocal);
        } else {
            connectionSecuritySeparator.collapse();
            connectionSecuritySeparator.setEnabled(false);
            connectionStringsSeparator.collapse();
            connectionStringsSeparator.setEnabled(false);
        }
    }
}
