package org.matsim.project;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;

import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.HbefaRoadTypeMapping;
import org.matsim.contrib.emissions.OsmHbefaMapping;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
// import org.matsim.core.controler.Injector;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;

import com.google.inject.Injector;

import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.utils.*;

import org.matsim.utils.objectattributes.attributable.Attributes;
import java.util.Random;

public class RunMatsimEmission {
    public void run(String inputNetworkFilename, String outputNetworkFilename) {
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(inputNetworkFilename);
        for (Link link : network.getLinks().values()) {
            if (link.getFreespeed() <= (50 / 3.6)) {
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE, "URB/Local/50");
            } else {
                // TODO adapt to real hbefa values
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE, "URB/Local/60");
            }
        }
        new NetworkWriter(network).write(outputNetworkFilename);
    }

    public static void main(String[] args) {
        // new NetworkMappingForEmission().run("network.xml.gz", "emission_net.xml.gz");

        Config config = ConfigUtils.loadConfig(
                "my_scenario/config.xml",
                new EmissionsConfigGroup());

        // get config file from args
        // if (args.length == 0) {
        // System.out.println("Please provide a config file as argument.");
        // System.exit(1);
        // }
        // System.out.println("Using config file: " + args[0]);
        // Config config = ConfigUtils.loadConfig(args[0], new EmissionsConfigGroup());

        Scenario scenario = ScenarioUtils.loadScenario(config);

        // HbefaRoadTypeMapping ohtn = OsmHbefaMapping.build();
        // ohtn.addHbefaMappings(scenario.getNetwork());

        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (link.getFreespeed() <= (50 / 3.6)) {
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                        "URB/Local/50");
            } else {
                // TODO adapt to real hbefa values
                // link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                // "URB/Local/80");
                link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
                        "URB/Local/60");
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

        // change population to 5% of original
        // scenario.getPopulation().getPersons().values().removeIf(person -> new Random().nextDouble() > 0.02);

        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(EmissionModule.class).asEagerSingleton();
            }
        });

        EmissionEventHandler my_handler = new EmissionEventHandler(scenario);
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().toInstance(my_handler);
            }
        });
        controler.run();
        
        String outputDir = config.controler().getOutputDirectory();
        my_handler.writeToJson(outputDir + "/emissions.json");

        // Config config = ConfigUtils.loadConfig("my_scenario/config_emission.xml", new
        // EmissionsConfigGroup());
        // final Scenario scenario = ScenarioUtils.loadScenario(config);

        // // HbefaRoadTypeMapping ohtn = OsmHbefaMapping.build();
        // // ohtn.addHbefaMappings(scenario.getNetwork());

        // for (Link link : scenario.getNetwork().getLinks().values()) {
        // if (link.getFreespeed() <= (50 / 3.6)) {
        // link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
        // "URB/Local/50");
        // } else {
        // // TODO adapt to real hbefa values
        // link.getAttributes().putAttribute(EmissionUtils.HBEFA_ROAD_TYPE,
        // "URB/Local/80");
        // }
        // }

        // final EventsManager eventsManager = EventsUtils.createEventsManager();
        // AbstractModule module = new AbstractModule() {
        // public void install() {
        // this.bind(Scenario.class).toInstance(scenario);
        // this.bind(EventsManager.class).toInstance(eventsManager);
        // this.bind(EmissionModule.class);
        // }
        // };
        // Injector injector = org.matsim.core.controler.Injector.createInjector(config,
        // module);
        // EmissionModule emissionModule = injector.getInstance(EmissionModule.class);
        // EventWriterXML emissionEventWriter = new
        // EventWriterXML("my_scenario/output/output_emission_events.xml");
        // emissionModule.getEmissionEventsManager().addHandler(emissionEventWriter);
        // eventsManager.initProcessing();
        // MatsimEventsReader matsimEventsReader = new
        // MatsimEventsReader(eventsManager);
        // matsimEventsReader.readFile("my_scenario/output/output_events.xml.gz"); //
        // existing events file as input
        // eventsManager.finishProcessing();
        // emissionEventWriter.closeFile();
    }
}
