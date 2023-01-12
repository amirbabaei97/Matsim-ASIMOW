package org.matsim.project;


import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.io.PopulationReader;

import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.mobsim.qsim.pt.TransitVehicle;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.*;
import org.matsim.vehicles.MatsimVehicleReader.VehicleReader;
import org.matsim.pt.*;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

public class CreateEmissionVehicles {
    public void run(String inputPopulationFilename, String outputVehiclesFilename) {
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
        new MatsimVehicleWriter(vehicles).writeFile(outputVehiclesFilename);

    }

    // public void addTransitVehiclesEmissionType(String inputVehiclesFilename){
    //     Vehicles vehicles = VehicleReader
    // }

    public static void main(String[] args) {
        new CreateEmissionVehicles().run("my_scenario/plans_3.xml.gz", "my_scenario/vehicles.xml.gz");
    }
}