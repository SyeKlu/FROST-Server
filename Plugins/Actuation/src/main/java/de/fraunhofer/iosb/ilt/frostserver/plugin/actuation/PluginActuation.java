/*
 * Copyright (C) 2020 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.plugin.actuation;

import de.fraunhofer.iosb.ilt.frostserver.model.EntityType;
import de.fraunhofer.iosb.ilt.frostserver.model.ModelRegistry;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.PersistenceManagerFactory;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.TableCollection;
import static de.fraunhofer.iosb.ilt.frostserver.plugin.actuation.ActuationModelSettings.TAG_ENABLE_ACTUATION;
import de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel.PluginCoreModel;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityPropertyMain;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain.NavigationPropertyEntity;
import de.fraunhofer.iosb.ilt.frostserver.property.NavigationPropertyMain.NavigationPropertyEntitySet;
import static de.fraunhofer.iosb.ilt.frostserver.property.SpecialNames.AT_IOT_ID;
import de.fraunhofer.iosb.ilt.frostserver.property.type.TypeComplex;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginModel;
import de.fraunhofer.iosb.ilt.frostserver.service.PluginRootDocument;
import de.fraunhofer.iosb.ilt.frostserver.service.Service;
import de.fraunhofer.iosb.ilt.frostserver.service.ServiceRequest;
import de.fraunhofer.iosb.ilt.frostserver.settings.ConfigDefaults;
import de.fraunhofer.iosb.ilt.frostserver.settings.CoreSettings;
import de.fraunhofer.iosb.ilt.frostserver.settings.Settings;
import de.fraunhofer.iosb.ilt.frostserver.util.LiquibaseUser;
import de.fraunhofer.iosb.ilt.frostserver.util.exception.UpgradeFailedException;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jooq.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author scf
 */
public class PluginActuation implements PluginRootDocument, PluginModel, ConfigDefaults, LiquibaseUser {

    private static final String LIQUIBASE_CHANGELOG_FILENAME = "liquibase/pluginactuation/tables";
    private static final String ACTUATOR = "Actuator";
    private static final String ACTUATORS = "Actuators";
    private static final String TASK = "Task";
    private static final String TASKS = "Tasks";
    private static final String TASKING_CAPABILITY = "TaskingCapability";
    private static final String TASKING_CAPABILITIES = "TaskingCapabilities";

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginActuation.class.getName());

    public final EntityPropertyMain<Map<String, Object>> epTaskingParameters = new EntityPropertyMain<>("taskingParameters", TypeComplex.STA_MAP, true, false);
    public EntityPropertyMain<?> epIdActuator;
    public EntityPropertyMain<?> epIdTask;
    public EntityPropertyMain<?> epIdTaskingCap;

    public final NavigationPropertyEntity npActuatorTaskCap = new NavigationPropertyEntity(ACTUATOR);
    public final NavigationPropertyEntity npThingTaskCap = new NavigationPropertyEntity("Thing");
    public final NavigationPropertyEntitySet npTasksTaskCap = new NavigationPropertyEntitySet(TASKS);

    public final NavigationPropertyEntity npTaskingCapabilityTask = new NavigationPropertyEntity(TASKING_CAPABILITY, npTasksTaskCap);
    public final NavigationPropertyEntitySet npTaskingCapabilitiesActuator = new NavigationPropertyEntitySet(TASKING_CAPABILITIES, npActuatorTaskCap);
    public final NavigationPropertyEntitySet npTaskingCapabilitiesThing = new NavigationPropertyEntitySet(TASKING_CAPABILITIES, npThingTaskCap);

    public final EntityType etActuator = new EntityType(ACTUATOR, ACTUATORS);
    public final EntityType etTask = new EntityType(TASK, TASKS);
    public final EntityType etTaskingCapability = new EntityType(TASKING_CAPABILITY, TASKING_CAPABILITIES);

    private static final List<String> REQUIREMENTS_ACTUATION = Arrays.asList(
            "http://www.opengis.net/spec/iot_tasking/1.0/req/tasking-capability",
            "http://www.opengis.net/spec/iot_tasking/1.0/req/task",
            "http://www.opengis.net/spec/iot_tasking/1.0/req/actuator",
            "http://www.opengis.net/spec/iot_tasking/1.0/req/create-tasks",
            "http://www.opengis.net/spec/iot_tasking/1.0/req/create-tasks-via-mqtt",
            "http://www.opengis.net/spec/iot_tasking/1.0/req/receive-updates-via-mqtt");

    private CoreSettings settings;
    private ActuationModelSettings modelSettings;
    private boolean enabled;
    private boolean fullyInitialised;

    public PluginActuation() {
        LOGGER.info("Creating new Actuation Plugin.");
    }

    @Override
    public void init(CoreSettings settings) {
        this.settings = settings;
        Settings pluginSettings = settings.getPluginSettings();
        enabled = pluginSettings.getBoolean(TAG_ENABLE_ACTUATION, ActuationModelSettings.class);
        if (enabled) {
            modelSettings = new ActuationModelSettings(settings);
            settings.getPluginManager().registerPlugin(this);
        }
    }

    @Override
    public boolean isFullyInitialised() {
        return fullyInitialised;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void modifyServiceDocument(ServiceRequest request, Map<String, Object> result) {
        Map<String, Object> serverSettings = (Map<String, Object>) result.get(Service.KEY_SERVER_SETTINGS);
        if (serverSettings == null) {
            // Nothing to add to.
            return;
        }
        Set<String> extensionList = (Set<String>) serverSettings.get(Service.KEY_CONFORMANCE_LIST);
        extensionList.addAll(REQUIREMENTS_ACTUATION);
    }

    @Override
    public void registerEntityTypes() {
        LOGGER.info("Initialising Actuation Types...");
        ModelRegistry mr = settings.getModelRegistry();

        mr.registerEntityType(etActuator);
        mr.registerEntityType(etTask);
        mr.registerEntityType(etTaskingCapability);

        epIdActuator = new EntityPropertyMain<>(AT_IOT_ID, mr.getPropertyType(modelSettings.idTypeActuator), "id");
        epIdTask = new EntityPropertyMain<>(AT_IOT_ID, mr.getPropertyType(modelSettings.idTypeTask), "id");
        epIdTaskingCap = new EntityPropertyMain<>(AT_IOT_ID, mr.getPropertyType(modelSettings.idTypeTaskingCap), "id");
    }

    @Override
    public boolean linkEntityTypes(PersistenceManager pm) {
        LOGGER.info("Linking Actuation Types...");
        final PluginCoreModel pluginCoreModel = settings.getPluginManager().getPlugin(PluginCoreModel.class);
        if (pluginCoreModel == null || !pluginCoreModel.isFullyInitialised()) {
            return false;
        }
        // ToDo: Fix IDs
        etActuator
                .registerProperty(epIdActuator, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(pluginCoreModel.epName, true)
                .registerProperty(pluginCoreModel.epDescription, true)
                .registerProperty(ModelRegistry.EP_ENCODINGTYPE, true)
                .registerProperty(pluginCoreModel.epMetadata, true)
                .registerProperty(ModelRegistry.EP_PROPERTIES, false)
                .registerProperty(npTaskingCapabilitiesActuator, false);
        etTask
                .registerProperty(epIdTask, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(pluginCoreModel.epCreationTime, false)
                .registerProperty(epTaskingParameters, true)
                .registerProperty(npTaskingCapabilityTask, true);
        etTaskingCapability
                .registerProperty(epIdTaskingCap, false)
                .registerProperty(ModelRegistry.EP_SELFLINK, false)
                .registerProperty(pluginCoreModel.epName, true)
                .registerProperty(pluginCoreModel.epDescription, true)
                .registerProperty(ModelRegistry.EP_PROPERTIES, false)
                .registerProperty(epTaskingParameters, true)
                .registerProperty(npActuatorTaskCap, true)
                .registerProperty(npTasksTaskCap, false)
                .registerProperty(npThingTaskCap, true);
        pluginCoreModel.etThing.registerProperty(npTaskingCapabilitiesThing, false);

        if (pm instanceof PostgresPersistenceManager) {
            PostgresPersistenceManager ppm = (PostgresPersistenceManager) pm;
            TableCollection tableCollection = ppm.getTableCollection();
            final DataType dataTypeActr = ppm.getDataTypeFor(modelSettings.idTypeActuator);
            final DataType dataTypeTask = ppm.getDataTypeFor(modelSettings.idTypeTask);
            final DataType dataTypeTCap = ppm.getDataTypeFor(modelSettings.idTypeTaskingCap);
            final DataType dataTypeThng = tableCollection.getTableForType(pluginCoreModel.etThing).getId().getDataType();
            tableCollection.registerTable(etActuator, new TableImpActuators(dataTypeActr, this, pluginCoreModel));
            tableCollection.registerTable(etTask, new TableImpTasks(dataTypeTask, dataTypeTCap, this, pluginCoreModel));
            tableCollection.registerTable(etTaskingCapability, new TableImpTaskingCapabilities(dataTypeTCap, dataTypeActr, dataTypeThng, this, pluginCoreModel));
        }
        fullyInitialised = true;
        return true;
    }

    @Override
    public String checkForUpgrades() {
        try (PersistenceManager pm = PersistenceManagerFactory.getInstance(settings).create()) {
            if (pm instanceof PostgresPersistenceManager) {
                PostgresPersistenceManager ppm = (PostgresPersistenceManager) pm;
                String fileName = LIQUIBASE_CHANGELOG_FILENAME + "IdLong" + ".xml";
                return ppm.checkForUpgrades(fileName);
            }
            return "Unknown persistence manager class";
        }
    }

    @Override
    public boolean doUpgrades(Writer out) throws UpgradeFailedException, IOException {
        try (PersistenceManager pm = PersistenceManagerFactory.getInstance(settings).create()) {
            if (pm instanceof PostgresPersistenceManager) {
                PostgresPersistenceManager ppm = (PostgresPersistenceManager) pm;
                String fileName = LIQUIBASE_CHANGELOG_FILENAME + "IdLong" + ".xml";
                return ppm.doUpgrades(fileName, out);
            }
            out.append("Unknown persistence manager class");
            return false;
        }
    }

}
