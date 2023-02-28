package org.matsim.project;

import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;


public class CalcScoreListener implements IterationEndsListener {

    private Scenario scenario;
    private String outputDir;
    private List<Double> meanList;
    private List<Double> varianceList;


    public CalcScoreListener(Scenario scenario, String outputDir){
        this.scenario = scenario;
        this.outputDir = outputDir;
        this.meanList = new ArrayList<>();
        this.varianceList = new ArrayList<>();
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        // Get the final plans for this iteration
        List<Plan> finalPlans = new ArrayList<>();
        for (Person person : this.scenario.getPopulation().getPersons().values()) {
            // only add plans from bg_traffic subpopulation or plans without subpopulation
            if (person.getAttributes().getAttribute("subpopulation") != "bg_traffic" ||
                    person.getAttributes().getAttribute("subpopulation") == null){
                finalPlans.add(person.getSelectedPlan());
            }
        }

        // get all scores and add to scoreList
        List<Double> scoreList = new ArrayList<>();
        double scoreSum = 0;
        for (Plan plan : finalPlans){
            scoreList.add(plan.getScore());
            scoreSum += plan.getScore();
        }
        
        // calculate mean and add to meanList
        meanList.add(scoreSum/scoreList.size());
        
        // calculate variance and add to varianceList
        double variance = 0;
        for (int i=0; i<scoreList.size(); i++){
            variance += Math.pow(scoreList.get(i)- meanList.get(event.getIteration()), 2);
        }
        varianceList.add(variance/scoreList.size());
        
        // write mean and variance to file
        try {
            FileWriter writer = new FileWriter(this.outputDir + "/mean_variance.csv", true);
            // add a header to the file
            if (event.getIteration() == 0){
                writer.write("Iteration,Mean,Variance\n");
            }
            writer.write(event.getIteration() + "," + meanList.get(event.getIteration()) + "," + varianceList.get(event.getIteration()) + "\n");
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
