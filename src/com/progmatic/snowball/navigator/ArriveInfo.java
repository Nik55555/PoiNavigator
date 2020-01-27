package com.progmatic.snowball.navigator;

public class ArriveInfo {
    public double durationFromStart = Dejkstra.INFINITY;
    public double durationToFinish = Dejkstra.INFINITY;
    public SkiArea.Transition transitionFromStart = null;
    public SkiArea.Transition transitionToFinish = null;
    protected boolean used = false;

    ArriveInfo() {
    }

    ArriveInfo(SkiArea.Transition transitionFromStart, SkiArea.Transition transitionToFinish) {
        this.transitionFromStart = transitionFromStart;
        this.transitionToFinish = transitionToFinish;
    }

    @Override
    public String toString() {
        return "ArriveInfo{" +
                "durationFromStart=" + durationFromStart +
                ", durationToFinish=" + durationToFinish +
                ", transitionFromStart=" + transitionFromStart.getTransitionId() +
                ", transitionToFinish=" + transitionToFinish.getTransitionId() +
                '}';
    }
}
