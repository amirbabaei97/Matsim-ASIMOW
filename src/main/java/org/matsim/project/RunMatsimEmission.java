package org.matsim.project;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;

import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.HbefaRoadTypeMapping;
import org.matsim.contrib.emissions.OsmHbefaMapping;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.api.core.v01.Id;
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
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehiclesFactory;
// import transit schedule related packages
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.DepartureImpl;
import org.matsim.pt.transitSchedule.TransitScheduleFactoryImpl;
import org.matsim.pt.transitSchedule.api.Departure;

import com.google.inject.Injector;

import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.utils.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;

import java.io.FileWriter;

import org.apache.log4j.Logger;
import org.geotools.util.logging.Logging;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.matsim.core.mobsim.qsim.qnetsimengine.DefaultTurnAcceptanceLogic;

import org.matsim.api.core.v01.network.Node;

public class RunMatsimEmission {

    public static void main(String[] args) {
        // new NetworkMappingForEmission().run("network.xml.gz", "emission_net.xml.gz");
        // Logger logger = Logger.getLogger(DefaultTurnAcceptanceLogic.class);
        // Logger logger =
        // LogManager.getLogger("org.matsim.core.mobsim.qsim.qnetsimengine.DefaultTurnAcceptanceLogic");
        // logger.setLevel(Level.OFF);
        // Logging.le

        System.out.println("Base with Berlin config");

        Config config = ConfigUtils.loadConfig(
                "my_scenario/berlin.config.xml",
                new EmissionsConfigGroup());

        String outputDir = config.controler().getOutputDirectory();

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

        int num = 0;
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

            // if speed limit is between 30 - 50 km/h, make it 30 km/h
            // if (link.getFreespeed() > (30 / 3.6) && link.getFreespeed() <= (52 / 3.6)) {
            //     // check if id not starts with pt
            //     if (!link.getId().toString().startsWith("pt")) {
            //         link.setFreespeed(30 / 3.6);
            //     }
            // }

            // try {
            // String type =
            // link.getAttributes().getAttribute("osm:way:highway").toString();
            // if (type.contains("motorway")) {
            // link.setFreespeed(30 / 3.6);
            // num++;
            // }
            // } catch (Exception e) {
            // // System.out.println("no osm:way:highway");
            // }

        }
        System.out.println("num: " + num);

        for (VehicleType vehicleType : scenario.getTransitVehicles().getVehicleTypes().values()) {
            Attributes engineAttributes = vehicleType.getEngineInformation().getAttributes();
            engineAttributes.putAttribute("HbefaVehicleCategory", "pass. car");
            engineAttributes.putAttribute("HbefaTechnology", "average");
            engineAttributes.putAttribute("HbefaSizeClass", "average");
            engineAttributes.putAttribute("HbefaEmissionsConcept", "average");
            // TODO: If want to add other vehicles types, change here
        }

        // double pt vehicles capacity
        // for (VehicleType vehicleType :
        // scenario.getTransitVehicles().getVehicleTypes().values()) {
        // if (vehicleType.getCapacity().getSeats() > 0) {
        // vehicleType.getCapacity().setSeats((int)
        // (vehicleType.getCapacity().getSeats() * 2));
        // }
        // }

        // change pt frequency

        // int factor = 3; // make frequency 3 times higher
        // TransitSchedule tschedule = scenario.getTransitSchedule();
        // Vehicles tVehicles = scenario.getTransitVehicles();
        // TransitScheduleFactoryImpl tscheduleFactory = new TransitScheduleFactoryImpl();
        // VehiclesFactory tVehiclesFactory = VehicleUtils.getFactory();
        // for (TransitLine tline : tschedule.getTransitLines().values()) {
        //     for (TransitRoute troute : tline.getRoutes().values()) {
        //         Departure[] departures = troute.getDepartures().values().toArray(new Departure[0]);
        //         for (int i = 0; i < departures.length - 1; i++) {
        //             Departure curDeparture = departures[i];
        //             Departure nextDeparture = departures[i + 1];
        //             VehicleType vehicleType = tVehicles.getVehicles().get(curDeparture.getVehicleId()).getType();
        //             double interval = (nextDeparture.getDepartureTime() -
        //                     curDeparture.getDepartureTime()) / factor;
        //             for (int j = 1; j < factor; j++) {
        //                 Id<Vehicle> vehicleId = Id
        //                         .createVehicleId(curDeparture.getVehicleId().toString() + "_" + factor +
        //                                 "_" + j);
        //                 Vehicle newVehicle = tVehiclesFactory.createVehicle(vehicleId, vehicleType);
        //                 tVehicles.addVehicle(newVehicle);
        //                 Id<Departure> depid = Id.create(curDeparture.getId().toString() + "_" + j,
        //                         Departure.class);
        //                 Departure newDeparture = tscheduleFactory.createDeparture(depid,
        //                         curDeparture.getDepartureTime() + j * interval);
        //                 newDeparture.setVehicleId(vehicleId);
        //                 troute.addDeparture(newDeparture);
        //             }
        //         }
        //     }
        // }

        // change population to 5% of original
        // float populationSize = scenario.getPopulation().getPersons().size();
        // float sizeWeWantPerc = 400_000 / populationSize;
        // scenario.getPopulation().getPersons().values().removeIf(person -> new
        // Random().nextDouble() > sizeWeWantPerc);
        // scenario.getPopulation().getPersons().values().removeIf(person -> new
        // Random().nextDouble() > 0.1);

        // remove if person id starts with "back"
        scenario.getPopulation().getPersons().values().removeIf(person -> person.getId().toString().startsWith("back"));
        // print population size
        System.out.println("Population size: " + scenario.getPopulation().getPersons().size());
        // sleep for 5 seconds
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Controler controler = new Controler(scenario);

        CalcScoreListener calcScoreListener = new CalcScoreListener(scenario,
                outputDir);
        controler.addControlerListener(calcScoreListener);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(EmissionModule.class).asEagerSingleton();
            }
        });

        // EmissionEventHandler my_handler = new EmissionEventHandler(scenario);
        // controler.addOverridingModule(new AbstractModule() {
        // @Override
        // public void install() {
        // addEventHandlerBinding().toInstance(my_handler);
        // }
        // });

        // AgentTrackerHandler agentTrackerHandler = new AgentTrackerHandler(scenario);
        // controler.addOverridingModule(new AbstractModule() {
        // @Override
        // public void install() {
        // addEventHandlerBinding().toInstance(agentTrackerHandler);
        // }
        // });

        controler.run();

        // my_handler.writeToJson(outputDir + "/emissions.json");
        // agentTrackerHandler.writeToFile(outputDir + "/agentTracker.csv");
    }
}
