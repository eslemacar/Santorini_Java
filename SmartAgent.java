// SmartAgent.java
// Extends ReflexAgent to provide convenience training hooks (save/load + notifyGameEnd)


import java.io.IOException;


public class SmartAgent extends ReflexAgent {


    public SmartAgent(String playerId) {
        super(playerId);
    }


    /**
     * Called by the trainer at the end of a game to pass the final reward.
     * This will update weights and persist them to disk.
     */
    public void notifyGameEnd(double finalReward) {
// Update weights based on feature history collected during the game
        updateWeights(finalReward);
// Save updated weights so training persists across runs
        try {
            saveWeights();
        } catch (Exception e) {
// ReflexAgent.saveWeights prints its own message on failure; ignore here
        }
    }
}


