package cric;

/**
 * Holds the current session (match) and innings ids so UIs can share state
 */
public class MatchContext {
    public static int sessionId = 0;      // one per match (both innings)
    public static int innings1Id = 0;     // DB id for first innings row
    public static int innings2Id = 0;
}