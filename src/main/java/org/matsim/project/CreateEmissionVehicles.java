package org.matsim.project;


import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.prepare.population.MergePopulations;
import org.matsim.core.population.io.PopulationReader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.*;
import org.matsim.vehicles.MatsimVehicleReader.VehicleReader;

import jakarta.validation.OverridesAttribute.List;

import org.matsim.pt.*;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

public class CreateEmissionVehicles {
    public Vehicles pop_to_vehicles(String inputPopulationFilename) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFilename);
        Vehicles vehicles = scenario.getVehicles();
        VehiclesFactory factory = vehicles.getFactory();
        VehicleType avgCarType = factory.createVehicleType(Id.create("car_average", VehicleType.class));
        Attributes engineAttributes = avgCarType.getEngineInformation().getAttributes();
        engineAttributes.putAttribute("HbefaVehicleCategory", "pass. car");
        engineAttributes.putAttribute("HbefaTechnology", "average");
        engineAttributes.putAttribute("HbefaSizeClass", "average");
        engineAttributes.putAttribute("HbefaEmissionsConcept", "average");
        vehicles.addVehicleType(avgCarType);
        VehicleType petrolCarType = factory.createVehicleType(Id.create("car_petrol", VehicleType.class));
        engineAttributes = petrolCarType.getEngineInformation().getAttributes();
        engineAttributes.putAttribute("HbefaVehicleCategory", "pass. car");
        engineAttributes.putAttribute("HbefaTechnology", "petrol (4S)");
        engineAttributes.putAttribute("HbefaSizeClass", ">=2L");
        engineAttributes.putAttribute("HbefaEmissionsConcept", "PC-P-Euro-1");
        vehicles.addVehicleType(petrolCarType);

        Random r = new Random(123456);
        for (Person person : scenario.getPopulation().getPersons().values()) {
            VehicleType vehType = avgCarType;
            // randomly assign vehicle types in this example
            if (r.nextDouble() < 0.5) {
                vehType = petrolCarType;
            }
            Vehicle vehicle = factory.createVehicle(Id.create(person.getId().toString(), Vehicle.class), vehType);
            vehicles.addVehicle(vehicle);
        }
        // add code for public transport vehicles here if necessary (see next slide)
        // new MatsimVehicleWriter(vehicles).writeFile(outputVehiclesFilename);
        return vehicles;

    }
    public Vehicles traffic_agents_to_Vehicles(String inputPopulationFilename) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulationFilename);
        Vehicles vehicles = scenario.getVehicles();
        VehiclesFactory factory = vehicles.getFactory();
        // a list of "coach", "HGV", "LCV", "motorcycle", "pass. car", "urban bus"
        String[] vehicleTypes = {"coach", "HGV", "LCV", "motorcycle", "pass. car", "urban bus"};
        Map<String, VehicleType> vehTypeMap = new HashMap<String, VehicleType>();
        for (String vehicleType : vehicleTypes) {
            VehicleType vehType = factory.createVehicleType(Id.create(vehicleType, VehicleType.class));
            Attributes engineAttributes = vehType.getEngineInformation().getAttributes();
            engineAttributes.putAttribute("HbefaVehicleCategory", vehicleType);
            engineAttributes.putAttribute("HbefaTechnology", "average");
            engineAttributes.putAttribute("HbefaSizeClass", "average");
            engineAttributes.putAttribute("HbefaEmissionsConcept", "average");
            vehicles.addVehicleType(vehType);
            vehTypeMap.put(vehicleType, vehType);
        }        
        for (Person person : scenario.getPopulation().getPersons().values()) {
            String subpop = (String) person.getAttributes().getAttribute("subpopulation");
            String VehicleTypeStr = person.getId().toString().split("-")[2];
            VehicleType vehType = vehTypeMap.get(VehicleTypeStr);
            Vehicle vehicle = factory.createVehicle(Id.create(person.getId().toString(), Vehicle.class), vehType);
            vehicles.addVehicle(vehicle);
        }
        // add code for public transport vehicles here if necessary (see next slide)
        // new MatsimVehicleWriter(vehicles).writeFile(outputVehiclesFilename);
        return vehicles;
    }

    public void createEmissionVehicles(String inputPopulationFilename, String outputVehiclesFilename) {
        Vehicles vehicles = pop_to_vehicles(inputPopulationFilename);
        new MatsimVehicleWriter(vehicles).writeFile(outputVehiclesFilename);
    }

    public Population mergeTwoPopulations(Population population1, Population population2) {
        Population mergedPopulation = population1;
        for (Person person : population2.getPersons().values()) {
            mergedPopulation.addPerson(person);
        }
        return mergedPopulation;
    }

    public void createEmissionVehiclesAndTrafficAgents(String inputPopulationFilename, String inputTrafficAgentsPopulationFilename, String outputVehiclesFilename) {
        Vehicles vehicles = pop_to_vehicles(inputPopulationFilename);
        Vehicles trafficAgentsVehicles = traffic_agents_to_Vehicles(inputTrafficAgentsPopulationFilename);
        Population population1 = PopulationUtils.readPopulation(inputPopulationFilename);
        Population population2 = PopulationUtils.readPopulation(inputTrafficAgentsPopulationFilename);
        Population mergedPopulation = mergeTwoPopulations(population1, population2);
        
        Vehicles mergedVehicles = VehicleUtils.createVehiclesContainer();
        for (VehicleType vehicleType : vehicles.getVehicleTypes().values()) {
            mergedVehicles.addVehicleType(vehicleType);
        }
        for (VehicleType vehicleType : trafficAgentsVehicles.getVehicleTypes().values()) {
            mergedVehicles.addVehicleType(vehicleType);
        }
        for (Vehicle vehicle : vehicles.getVehicles().values()) {
            mergedVehicles.addVehicle(vehicle);
        }
        for (Vehicle vehicle : trafficAgentsVehicles.getVehicles().values()) {
            mergedVehicles.addVehicle(vehicle);
        }
        new MatsimVehicleWriter(mergedVehicles).writeFile(outputVehiclesFilename);
        PopulationUtils.writePopulation(mergedPopulation, "my_scenario/merged_plans.xml.gz");

    }
    // public void addTransitVehiclesEmissionType(String inputVehiclesFilename){
    //     Vehicles vehicles = VehicleReader
    // }

    public static void main(String[] args) {
        // new CreateEmissionVehicles().createEmissionVehicles("my_scenario/plans_3.xml.gz", "my_scenario/vehicles.xml.gz");
        new CreateEmissionVehicles().createEmissionVehiclesAndTrafficAgents("my_scenario/plans_3.xml.gz", "my_scenario/traffic_plans.xml.gz", "my_scenario/merged_vehicles.xml.gz");
    }
}