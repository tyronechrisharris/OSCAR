package com.botts.impl.process.occupancy.rs350;

import org.sensorhub.api.module.IModuleProvider;
import org.sensorhub.api.processing.IProcessProvider;
import org.sensorhub.impl.module.AbstractActivator;

public class Activator extends AbstractActivator {
    @Override
    protected Iterable<Class<? extends IModuleProvider>> getModuleProviders() {
        return list(RS350OccupancyProcessModule.class);
    }

    @Override
    protected Iterable<Class<? extends IProcessProvider>> getProcessProviders() {
        return list(RS350OccupancyProcessModule.class);
    }
}
