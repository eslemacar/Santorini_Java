// SmartAgent.java
// Extends ReflexAgent
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

        updateWeights(finalReward);

        try {
            saveWeights();
        } catch (Exception e) {

        }
    }
}


