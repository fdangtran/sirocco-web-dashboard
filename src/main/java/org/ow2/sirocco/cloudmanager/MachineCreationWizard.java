/**
 *
 * SIROCCO
 * Copyright (C) 2013 France Telecom
 * Contact: sirocco@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 */
package org.ow2.sirocco.cloudmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import org.ow2.sirocco.cloudmanager.MachineView.MachineBean;
import org.ow2.sirocco.cloudmanager.core.api.ICloudProviderManager;
import org.ow2.sirocco.cloudmanager.core.api.ICredentialsManager;
import org.ow2.sirocco.cloudmanager.core.api.IMachineImageManager;
import org.ow2.sirocco.cloudmanager.core.api.IMachineManager;
import org.ow2.sirocco.cloudmanager.core.api.INetworkManager;
import org.ow2.sirocco.cloudmanager.core.api.exception.CloudProviderException;
import org.ow2.sirocco.cloudmanager.model.cimi.Credentials;
import org.ow2.sirocco.cloudmanager.model.cimi.Job;
import org.ow2.sirocco.cloudmanager.model.cimi.Machine;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineConfiguration;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineImage;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineTemplate;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineTemplateNetworkInterface;
import org.ow2.sirocco.cloudmanager.model.cimi.Network;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.CloudProviderAccount;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.ProviderMapping;
import org.vaadin.teemu.wizards.Wizard;
import org.vaadin.teemu.wizards.WizardStep;
import org.vaadin.teemu.wizards.event.WizardCancelledEvent;
import org.vaadin.teemu.wizards.event.WizardCompletedEvent;
import org.vaadin.teemu.wizards.event.WizardProgressListener;
import org.vaadin.teemu.wizards.event.WizardStepActivationEvent;
import org.vaadin.teemu.wizards.event.WizardStepSetChangedEvent;

import com.vaadin.cdi.UIScoped;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.util.BeanContainer;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.shared.ui.dd.VerticalDropLocation;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.TableDragMode;
import com.vaadin.ui.Table.TableTransferable;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

@UIScoped
@SuppressWarnings("serial")
public class MachineCreationWizard extends Window implements WizardProgressListener {
    private MachineView machineView;

    private Wizard wizard;

    private Util.PlacementStep placementStep;

    private Util.MetadataStep metadataStep;

    private ImageStep imageStep;

    private ConfigStep configStep;

    private NetworkStep networkStep;

    private KeyPairStep keyPairStep;

    private UserDataStep userDataStep;

    @Inject
    private ICloudProviderManager providerManager;

    @Inject
    private IMachineManager machineManager;

    @Inject
    private IMachineImageManager machineImageManager;

    @Inject
    private ICredentialsManager credentialsManager;

    @Inject
    private INetworkManager networkManager;

    public MachineCreationWizard() {
        super("Machine Creation");
        this.center();
        this.setClosable(false);
        this.setModal(true);
        this.setResizable(false);

        VerticalLayout content = new VerticalLayout();
        content.setMargin(true);
        this.wizard = new Wizard();
        this.wizard.addListener(this);
        this.wizard.addStep(this.placementStep = new Util.PlacementStep(this.wizard), "placement");
        this.placementStep.setListener(new Property.ValueChangeListener() {

            @Override
            public void valueChange(final ValueChangeEvent event) {
                MachineCreationWizard.this.updateProviderSpecificResources();
            }
        });
        this.wizard.addStep(this.metadataStep = new Util.MetadataStep(this.wizard), "metadata");
        this.wizard.addStep(this.imageStep = new ImageStep(), "Image");
        this.wizard.addStep(this.configStep = new ConfigStep(), "Hardware");
        this.wizard.addStep(this.networkStep = new NetworkStep(), "Network");
        this.wizard.addStep(this.keyPairStep = new KeyPairStep(), "Key pair");
        this.wizard.addStep(this.userDataStep = new UserDataStep(), "User data");
        this.wizard.setHeight("300px");
        this.wizard.setWidth("560px");

        content.addComponent(this.wizard);
        content.setComponentAlignment(this.wizard, Alignment.TOP_CENTER);
        this.setContent(content);
    }

    public void init(final MachineView machineView) {
        this.machineView = machineView;
        this.wizard.setUriFragmentEnabled(false);
        this.wizard.activateStep(this.placementStep);
        String tenantId = ((MyUI) UI.getCurrent()).getTenantId();

        this.placementStep.providerBox.removeAllItems();
        try {
            this.placementStep.setProviderManager(this.providerManager);
            for (CloudProviderAccount providerAccount : this.providerManager.getCloudProviderAccountsByTenant(tenantId)) {
                this.placementStep.providerBox.addItem(providerAccount.getId().toString());
                this.placementStep.providerBox.setItemCaption(providerAccount.getId().toString(), providerAccount
                    .getCloudProvider().getDescription());
            }
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Internal error", e);
        }

        this.metadataStep.nameField.setValue("");
        this.metadataStep.descriptionField.setValue("");

        this.keyPairStep.keyPairBox.removeAllItems();
        try {
            for (Credentials cred : this.credentialsManager.getCredentials()) {
                this.keyPairStep.keyPairBox.addItem(cred.getId());
                this.keyPairStep.keyPairBox.setItemCaption(cred.getId(), cred.getName());
            }
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Internal error", e);
        }
        this.updateProviderSpecificResources();
    }

    private void updateProviderSpecificResources() {
        this.imageStep.imageBox.removeAllItems();
        try {
            for (MachineImage image : this.machineImageManager.getMachineImages()) {
                ProviderMapping mapping = ProviderMapping.find(image, this.getSelectedProviderAccountId(),
                    this.getSelectedCountry());
                if (mapping == null) {
                    continue;
                }
                this.imageStep.imageBox.addItem(image.getId());
                this.imageStep.imageBox.setItemCaption(image.getId(), image.getName());
            }
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Internal error", e);
        }
        this.configStep.configBox.removeAllItems();
        try {
            for (MachineConfiguration config : this.machineManager.getMachineConfigurations()) {
                ProviderMapping mapping = ProviderMapping.find(config, this.getSelectedProviderAccountId(),
                    this.getSelectedCountry());
                if (mapping == null) {
                    continue;
                }
                this.configStep.configBox.addItem(config.getId());
                this.configStep.configBox.setItemCaption(config.getId(), config.getName());
            }
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Internal error", e);
        }
        this.networkStep.nics.removeAllItems();
        this.networkStep.nets.removeAllItems();
        try {
            for (Network net : this.networkManager.getNetworks()) {
                if (net.getCloudProviderAccount().getId().toString().equals(this.getSelectedProviderAccountId())
                    && net.getLocation().getCountryName().equals(this.getSelectedCountry())) {
                    this.networkStep.nets.addBean(new NetBean(net));
                }
            }
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Internal error", e);
        }
    }

    @Override
    public void activeStepChanged(final WizardStepActivationEvent event) {
        if (event.getActivatedStep() == this.metadataStep) {
            this.metadataStep.nameField.focus();
        }
    }

    @Override
    public void stepSetChanged(final WizardStepSetChangedEvent event) {
    }

    private String getSelectedProviderAccountId() {
        return (String) MachineCreationWizard.this.placementStep.providerBox.getValue();
    }

    private String getSelectedCountry() {
        return (String) MachineCreationWizard.this.placementStep.locationBox.getValue();
    }

    @Override
    public void wizardCompleted(final WizardCompletedEvent event) {
        this.close();

        MachineCreate machineCreate = new MachineCreate();
        machineCreate.setProperties(new HashMap<String, String>());

        try {
            String accountId = this.getSelectedProviderAccountId();
            machineCreate.getProperties().put("providerAccountId", accountId);
            machineCreate.getProperties().put("location", this.getSelectedCountry());
            machineCreate.setName(MachineCreationWizard.this.metadataStep.nameField.getValue());
            machineCreate.setDescription(MachineCreationWizard.this.metadataStep.descriptionField.getValue());
            if (machineCreate.getDescription().isEmpty()) {
                machineCreate.setDescription(null);
            }
            MachineTemplate machineTemplate = new MachineTemplate();
            MachineConfiguration machineConfig = MachineCreationWizard.this.machineManager
                .getMachineConfigurationById(((Integer) MachineCreationWizard.this.configStep.configBox.getValue()).toString());
            machineTemplate.setMachineConfig(machineConfig);
            MachineImage machineImage = MachineCreationWizard.this.machineImageManager
                .getMachineImageById(((Integer) MachineCreationWizard.this.imageStep.imageBox.getValue()).toString());
            machineTemplate.setMachineImage(machineImage);

            // network interfaces
            List<MachineTemplateNetworkInterface> nics = new ArrayList<>();
            machineTemplate.setNetworkInterfaces(nics);
            for (Object itemId : MachineCreationWizard.this.networkStep.nicTable.getItemIds()) {
                NicBean nicBean = MachineCreationWizard.this.networkStep.nics.getItem(itemId).getBean();
                MachineTemplateNetworkInterface nic = new MachineTemplateNetworkInterface();
                nic.setNetwork(nicBean.net);
                nics.add(nic);
            }

            if (MachineCreationWizard.this.keyPairStep.keyPairBox.getValue() != null) {
                Credentials cred = MachineCreationWizard.this.credentialsManager
                    .getCredentialsById(((Integer) MachineCreationWizard.this.keyPairStep.keyPairBox.getValue()).toString());
                machineTemplate.setCredential(cred);
            }

            machineTemplate.setUserData(MachineCreationWizard.this.userDataStep.userDataField.getValue());
            if (machineTemplate.getUserData().isEmpty()) {
                machineTemplate.setUserData(null);
            }

            machineCreate.setMachineTemplate(machineTemplate);

            Job job = MachineCreationWizard.this.machineManager.createMachine(machineCreate);
            Machine newMachine = (Machine) job.getAffectedResources().get(0);

            MachineCreationWizard.this.machineView.machines.addBeanAt(0, new MachineBean(newMachine));

            UI.getCurrent().push();
        } catch (CloudProviderException e) {
            Util.diplayErrorMessageBox("Instance creation failure", e);
        }

    }

    @Override
    public void wizardCancelled(final WizardCancelledEvent event) {
        this.close();
    }

    private class ImageStep implements WizardStep {
        FormLayout content;

        ComboBox imageBox;

        ImageStep() {
            this.content = new FormLayout();
            this.content.setSizeFull();
            this.content.setMargin(true);

            this.imageBox = new ComboBox("Image");
            this.imageBox.setTextInputAllowed(false);
            this.imageBox.setNullSelectionAllowed(false);
            this.imageBox.setInputPrompt("select image");
            this.imageBox.setImmediate(true);
            this.content.addComponent(this.imageBox);
            this.imageBox.addValueChangeListener(new Property.ValueChangeListener() {

                @Override
                public void valueChange(final ValueChangeEvent event) {
                    MachineCreationWizard.this.wizard.updateButtons();
                }
            });
        }

        @Override
        public String getCaption() {
            return "Image";
        }

        @Override
        public Component getContent() {
            return this.content;
        }

        @Override
        public boolean onAdvance() {
            return this.imageBox.getValue() != null;
        }

        @Override
        public boolean onBack() {
            return true;
        }

    }

    private class ConfigStep implements WizardStep {
        FormLayout content;

        ComboBox configBox;

        ConfigStep() {
            this.content = new FormLayout();
            this.content.setSizeFull();
            this.content.setMargin(true);

            this.configBox = new ComboBox("Hardware config");
            this.configBox.setTextInputAllowed(false);
            this.configBox.setNullSelectionAllowed(false);
            this.configBox.setInputPrompt("select config");
            this.configBox.setImmediate(true);
            this.content.addComponent(this.configBox);
            this.configBox.addValueChangeListener(new Property.ValueChangeListener() {

                @Override
                public void valueChange(final ValueChangeEvent event) {
                    MachineCreationWizard.this.wizard.updateButtons();
                }
            });
        }

        @Override
        public String getCaption() {
            return "Hardware";
        }

        @Override
        public Component getContent() {
            return this.content;
        }

        @Override
        public boolean onAdvance() {
            return this.configBox.getValue() != null;
        }

        @Override
        public boolean onBack() {
            return true;
        }

    }

    private class NetworkStep implements WizardStep {
        HorizontalLayout content;

        Table nicTable;

        Table netTable;

        private BeanContainer<Integer, NicBean> nics = new BeanContainer<Integer, NicBean>(NicBean.class);

        private BeanContainer<Integer, NetBean> nets = new BeanContainer<Integer, NetBean>(NetBean.class);

        NetworkStep() {
            this.content = new HorizontalLayout();
            this.content.setSizeFull();
            this.content.setMargin(true);
            this.content.setSpacing(true);

            this.nicTable = new Table("Network Interfaces", this.nics);
            this.nics.setBeanIdProperty("id");
            this.nicTable.setPageLength(4);
            this.nicTable.setSelectable(true);
            this.nicTable.setImmediate(true);
            this.nicTable.setVisibleColumns("network", "type");
            this.nicTable.setWidth("100%");
            this.nicTable.setDropHandler(new DropHandler() {

                @Override
                public AcceptCriterion getAcceptCriterion() {
                    return AcceptAll.get();
                }

                @Override
                public void drop(final DragAndDropEvent event) {
                    TableTransferable tableTransferable = (TableTransferable) event.getTransferable();
                    Integer netId = (Integer) tableTransferable.getItemId();
                    AbstractSelectTargetDetails dropData = ((AbstractSelectTargetDetails) event.getTargetDetails());
                    Integer targetItemId = (Integer) dropData.getItemIdOver();
                    VerticalDropLocation location = dropData.getDropLocation();

                    if (targetItemId == null) {
                        NetworkStep.this.nics.addBean(new NicBean(NetworkStep.this.nets.getItem(netId).getBean().net));
                    } else {

                        if (location == VerticalDropLocation.MIDDLE || location == VerticalDropLocation.TOP) {
                            Integer prevItemId = NetworkStep.this.nics.prevItemId(targetItemId);
                            NetworkStep.this.nics.addBeanAfter(prevItemId, new NicBean(NetworkStep.this.nets.getItem(netId)
                                .getBean().net));
                        } else {
                            NetworkStep.this.nics.addBeanAfter(targetItemId, new NicBean(NetworkStep.this.nets.getItem(netId)
                                .getBean().net));
                        }
                    }

                }
            });
            this.content.addComponent(this.nicTable);

            this.netTable = new Table("Networks", this.nets);
            this.nets.setBeanIdProperty("id");
            this.netTable.setPageLength(4);
            this.netTable.setSelectable(true);
            this.netTable.setImmediate(true);
            this.netTable.setVisibleColumns("network", "type");
            this.netTable.setWidth("100%");
            this.netTable.setDragMode(TableDragMode.ROW);
            this.content.addComponent(this.netTable);

            // HorizontalLayout buttonBar = new HorizontalLayout();
            // buttonBar.setMargin(true);
            // buttonBar.setSpacing(true);
            // Button button = new Button("Add NIC", new ClickListener() {
            //
            // @Override
            // public void buttonClick(ClickEvent event) {
            // }
            // });
            // buttonBar.addComponent(button);
            // button = new Button("Remove NIC", new ClickListener() {
            //
            // @Override
            // public void buttonClick(ClickEvent event) {
            // }
            // });
            // buttonBar.addComponent(button);
            // content.addComponent(buttonBar);

        }

        @Override
        public String getCaption() {
            return "Network";
        }

        @Override
        public Component getContent() {
            return this.content;
        }

        @Override
        public boolean onAdvance() {
            return true;
        }

        @Override
        public boolean onBack() {
            return true;
        }

    }

    private class KeyPairStep implements WizardStep {
        FormLayout content;

        ComboBox keyPairBox;

        KeyPairStep() {
            this.content = new FormLayout();
            this.content.setSizeFull();
            this.content.setMargin(true);

            this.keyPairBox = new ComboBox("Key pair");
            this.keyPairBox.setTextInputAllowed(false);
            this.keyPairBox.setNullSelectionAllowed(false);
            this.keyPairBox.setInputPrompt("select config");
            this.keyPairBox.setImmediate(true);
            this.content.addComponent(this.keyPairBox);
        }

        @Override
        public String getCaption() {
            return "Key pair";
        }

        @Override
        public Component getContent() {
            return this.content;
        }

        @Override
        public boolean onAdvance() {
            return true;
        }

        @Override
        public boolean onBack() {
            return true;
        }

    }

    private class UserDataStep implements WizardStep {
        FormLayout content;

        TextArea userDataField;

        UserDataStep() {
            this.content = new FormLayout();
            this.content.setSizeFull();
            this.content.setMargin(true);

            this.userDataField = new TextArea("User data");
            this.userDataField.setWidth("80%");
            this.content.addComponent(this.userDataField);
        }

        @Override
        public String getCaption() {
            return "User data";
        }

        @Override
        public Component getContent() {
            return this.content;
        }

        @Override
        public boolean onAdvance() {
            return true;
        }

        @Override
        public boolean onBack() {
            return true;
        }

    }

    public static class NicBean {
        Integer id;

        String network;

        String type;

        Network net;

        NicBean(final Network net) {
            this.net = net;
            this.id = net.getId();
            this.network = net.getName();
            this.type = net.getNetworkType().toString();
        }

        public Integer getId() {
            return this.id;
        }

        public String getNetwork() {
            return this.network;
        }

        public String getType() {
            return this.type;
        }
    }

    public static class NetBean {
        Integer id;

        String network;

        String type;

        Network net;

        NetBean(final Network net) {
            this.net = net;
            this.id = net.getId();
            this.network = net.getName();
            this.type = net.getNetworkType().toString();
        }

        public Integer getId() {
            return this.id;
        }

        public String getNetwork() {
            return this.network;
        }

        public String getType() {
            return this.type;
        }
    }

}
