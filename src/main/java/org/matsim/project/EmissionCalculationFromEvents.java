package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.project.EmissionEventHandler;
import org.matsim.utils.objectattributes.attributable.Attributes;

import com.google.inject.Injector;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Map;

public class EmissionCalculationFromEvents {

    public static void main(String[] args) {

        String inputDir = "output_last_config_bike_500_iter_10km_limit";
        Config config = ConfigUtils.loadConfig("my_scenario" + "/config_new.xml",
                new EmissionsConfigGroup());

        final Scenario scenario = ScenarioUtils.loadScenario(config);

        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (link.getFreespeed() <= (50 / 3.6)) {
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                        "URB/Local/50");
            } else {
                // link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                // "URB/Local/80");
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                        "URB/Local/60");
            }

            // if speed limit is between 30 - 50 km/h, make it 30 km/h
            if (link.getFreespeed() > (30 / 3.6) && link.getFreespeed() <= (52 / 3.6)) {
                // check if id not starts with pt
                if (!link.getId().toString().startsWith("pt")) {
                    link.setFreespeed(10 / 3.6);
                }
            }
        }

        for (VehicleType vehicleType : scenario.getTransitVehicles().getVehicleTypes().values()) {
            Attributes engineAttributes = vehicleType.getEngineInformation().getAttributes();
            engineAttributes.putAttribute("HbefaVehicleCategory", "pass. car");
            engineAttributes.putAttribute("HbefaTechnology", "average");
            engineAttributes.putAttribute("HbefaSizeClass", "average");
            engineAttributes.putAttribute("HbefaEmissionsConcept", "average");
            //TODO: If want to add other vehicles types, change here
        }

        final EventsManager eventsManager = EventsUtils.createEventsManager();
        AbstractModule module = new AbstractModule() {
            public void install() {
                this.bind(Scenario.class).toInstance(scenario);
                this.bind(EventsManager.class).toInstance(eventsManager);
                this.bind(EmissionModule.class);
            }
        };

        EmissionEventHandler emissionEventHandler = new EmissionEventHandler(scenario);
        eventsManager.addHandler(emissionEventHandler);
        String outputDir = config.controler().getOutputDirectory();

        Injector injector = org.matsim.core.controler.Injector.createInjector(config, module);
        EmissionModule emissionModule = injector.getInstance(EmissionModule.class);
        EventWriterXML emissionEventWriter = new EventWriterXML(inputDir + "/emissionEvents.xml.gz");
        emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);
        eventsManager.initProcessing();
        MatsimEventsReader matsimEventsReader = new MatsimEventsReader(eventsManager);
        matsimEventsReader.readFile(inputDir + "/output_events.xml.gz");
        eventsManager.finishProcessing();

        emissionEventHandler.writeToJson(outputDir + "/emission.json");
    }
}
