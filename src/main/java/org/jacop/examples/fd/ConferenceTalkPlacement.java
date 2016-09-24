package org.jacop.examples.fd;

import org.jacop.constraints.*;
import org.jacop.core.IntDomain;
import org.jacop.core.IntVar;
import org.jacop.core.IntervalDomain;
import org.jacop.core.Store;
import org.jacop.search.*;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * Author : Radoslaw Szymanek
 * Email : radoslaw.szymanek@osolpro.com
 * <p/>
 * Copyright 2012, All rights reserved.
 */
public class ConferenceTalkPlacement {

    Store store;
    IntVar cost;
    ArrayList<IntVar> vars;
    IntVar[][] varsMatrix;
    DepthFirstSearch<IntVar> search;

    private HashMap<Integer, HashMap<Integer, Integer>> transformCosts(int [][] costs, int noOfTalks) {

        HashMap<Integer, HashMap<Integer, Integer>> result = new HashMap<Integer, HashMap<Integer, Integer>>();

        for (int i = 0; i < noOfTalks; i++)
            result.put(i, new HashMap<Integer, Integer>());

        for (int i = 0; i < costs.length; i++)
            result.get(costs[i][0]).put(costs[i][1], costs[i][2]);

        System.out.println(result);

        return result;
    }

    private HashMap<Integer, HashMap<Integer, Integer>> randomCosts(int noOfTalks,
                                                                    int randomSeed,
                                                                    int maxSingleCost) {

        Random seed = new Random(randomSeed);

        HashMap<Integer, HashMap<Integer, Integer>> result = new HashMap<Integer, HashMap<Integer, Integer>>();

        for (int i = 0; i < noOfTalks; i++)
            result.put(i, new HashMap<Integer, Integer>());

        for (int i = 0; i < noOfTalks; i++)
            for (int j = i+ 1; j < noOfTalks; j++)
                result.get(i).put(j, seed.nextInt(maxSingleCost));

        return result;
    }

    // assumes noTalks = noOfParallelTracks * noOfTimeSlots
    private int computeLowerBound(int noOfParallelTracks,
                                  int noOfTimeSlots,
                                  HashMap<Integer, HashMap<Integer, Integer>> costs
                                  ) {

        ArrayList<Integer> costsList = new ArrayList<>();
        for ( HashMap<Integer, Integer> elH : costs.values())
            costsList.addAll( elH.values() );

        Integer[] sortedArray = costsList.toArray(new Integer[costsList.size()]);
        Arrays.sort( sortedArray );

        int noOfTalksInOneTimeSlot = noOfParallelTracks;
        int lowerBound = 0;
        for (int i = 0; i < noOfTimeSlots * ( noOfTalksInOneTimeSlot * (noOfTalksInOneTimeSlot - 1) / 2 ); i++ )
            lowerBound += sortedArray[i];

        System.out.println( lowerBound );
        return lowerBound;
    }

    public void model(int noOfParallelTracks,
                                int noOfTalks,
                                int noOfTimeSlots,
                                int maxSingleCost,
                                HashMap<Integer, HashMap<Integer, Integer>> costMap) {

        store = new Store();

        varsMatrix = new IntVar[noOfTalks*(noOfTalks-1)/2][3];

        IntVar[] talkPlacement = new IntVar[noOfTalks];

        for (int i = 0; i < noOfTalks; i++)
            talkPlacement[i] = new IntVar(store, "talk[" + i + "]-track", 0, noOfParallelTracks - 1);

        IntVar[] talkCounterInTrack = new IntVar[noOfParallelTracks];
        for (int i = 0; i < noOfParallelTracks; i++)
            talkCounterInTrack[i] = new IntVar(store, "noOfTalksIn-" + i + "-th-Track",
                                                noOfTalks / noOfParallelTracks - 1,
                                                noOfTimeSlots);

        for (int i = 0; i < noOfParallelTracks; i++)
            store.impose(new Count(talkPlacement, talkCounterInTrack[i], i));

        IntVar[] pairCosts = new IntVar[noOfTalks*(noOfTalks-1)/2];

        int pairNo = 0;
        for (int i = 0; i < noOfTalks; i++)
            for (int j = i+ 1; j < noOfTalks; j++) {

                pairCosts[pairNo] = new IntVar(store, "pair(" + i + ", " + j+ ")Cost", 0, maxSingleCost);

                varsMatrix[pairNo][0] = pairCosts[pairNo];
                varsMatrix[pairNo][1] = talkPlacement[i];
                varsMatrix[pairNo][2] = talkPlacement[j];

                if (costMap.get(i).get(j) != null) {

                    store.impose(new IfThenElse(new XeqY(talkPlacement[i], talkPlacement[j]),
                        new XeqC(pairCosts[pairNo], costMap.get(i).get(j)), new XeqC(pairCosts[pairNo], 0)));

                    IntervalDomain costPairDomain = new IntervalDomain(0, 0);
                    costPairDomain.unionAdapt(costMap.get(i).get(j));
                    store.impose(new In(pairCosts[pairNo], costPairDomain));
                }
                else
                    store.impose(new XeqC(pairCosts[pairNo], 0));

                pairNo++;
            }

        cost = new IntVar(store, "cost", 0, IntDomain.MaxInt);

        store.impose(new SumInt(store, pairCosts, "==", cost));

        vars = new ArrayList<>();
        for (int i = 0; i < talkPlacement.length; i++)
            vars.add(talkPlacement[i]);

       // store.print();
    }


    /**
     *
     * It uses MaxRegret variable ordering heuristic to search for a solution.
     * @return true if there is a solution, false otherwise.
     *
     */
    public boolean searchMaxRegretForMatrixOptimal(int timeOutSeconds) {

        long T1, T2, T;
        T1 = System.currentTimeMillis();

        search = new DepthFirstSearch<IntVar>();
        PrintOutListener<IntVar> solutionListener = new PrintOutListener<IntVar>();
        search.setSolutionListener(solutionListener);

        if (timeOutSeconds > 0)
            search.setTimeOut(timeOutSeconds);

        // pivot variable is at index 0.
        SelectChoicePoint<IntVar> select = new SimpleMatrixSelect<IntVar>(varsMatrix,
            new MaxRegret<IntVar>(),
            new SmallestDomain<IntVar>(),
            new IndomainMin<IntVar>());

        boolean result = search.labeling(store, select, cost);

        T2 = System.currentTimeMillis();
        T = T2 - T1;

        if (result)
            System.out.println("Variables : " + vars);
        else
            System.out.println("Failed to find any solution");

        System.out.println("\n\t*** Execution time = " + T + " ms");

        return result;

    }

    public boolean search(int maxCostAllowed, int timeOutSeconds) {

        if (maxCostAllowed != -1)
            store.impose(new XlteqC(cost, maxCostAllowed));

        long T1, T2, T;
        T1 = System.currentTimeMillis();

        search = new DepthFirstSearch<IntVar>();

        // pivot variable is at index 0.
        SelectChoicePoint<IntVar> select = new SimpleMatrixSelect<IntVar>(varsMatrix,
            new MaxRegret<IntVar>(),
            new SmallestDomain<IntVar>(),
            new IndomainMin<IntVar>());

        if (timeOutSeconds > 0)
            search.setTimeOut(timeOutSeconds);

        boolean result = search.labeling(store, select);

        T2 = System.currentTimeMillis();
        T = T2 - T1;

        if (result)
            System.out.println("Variables : " + vars);
        else
            System.out.println("Failed to find any solution");

        System.out.println("\n\t*** Execution time = " + T + " ms");

        return result;

    }
    /**
     * It executes the program to solve this Travelling Salesman Problem.
     * @param args no argument is used.
     */
    public static void main(String args[]) {

        int noOfParallelTracks = 6;
        int noOfTimeSlots = 6;
        int noOfTalks = noOfParallelTracks * noOfTimeSlots;
        int maxSingleCost = 50;
        int randomSeed = 55;

        ConferenceTalkPlacement example = new ConferenceTalkPlacement();

        // The first key in the main hashmap denotes the first (lower id value) talk in any pair.
        // The second key in the secondary hashmap denotes the second (higher id value) talk in any pair.
        // The value in the nested hashmap specify the cost if pair of talks (first key, second key) are scheduled
        // in the same time slot.
        // The goal is to minimize the sum of costs.
        HashMap<Integer, HashMap<Integer, Integer>> costMap = example.randomCosts(noOfTalks, randomSeed, maxSingleCost);

        example.model(noOfParallelTracks,
                      noOfTalks,
                      noOfTimeSlots,
                      maxSingleCost,
                      costMap);

        // example.store.print(); // Useful for small examples.

        // If you get the first time out then it means that the problem gets too difficult or you have setup the
        // maximum cost too low.
        int timeOutSeconds = 180;

        // Unlikely to finish anytime soon for random examples of size more than noOfParallelTracks=3, and noOfTimeSlots=3.
        // Real life examples maybe solvable to optimality for much larger sizes.

        if ( example.searchMaxRegretForMatrixOptimal(timeOutSeconds)) {
            System.out.println("Solution(s) found");
            return;
        }

        // Everytime you find a solution reduce the maximum cost by a bit (e.g. 5%).
/*
        int maximumCost = 1700;
        if ( example.search(maximumCost, timeOutSeconds)) {
            System.out.println("Solution found with cost " + example.cost);
        }
*/

        // example.store.print(); // Useful for small examples.
    }
}
