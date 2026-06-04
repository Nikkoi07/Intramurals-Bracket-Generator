import java.util.*;

public class TournamentBracket {
    private Match root;
    private Team[] teams;
    private int totalRounds;
    private List<Match> allMatches;
    private TournamentType tournamentType;
    private List<Match> losersBracketMatches;
    private Match grandFinals;
    private Map<Match, Match> winnerToLoserMatch;
    private Map<Match, Match> lbWinnerAdvanceMap;  // maps each LB match → the next LB match its winner feeds into

    // ── Play-In specific fields ───────────────────────────────────────────────
    // playInMatches    : all matches inside the DE play-in sub-bracket
    // mainBracketMatches: semifinals + final that the bye teams wait in
    // byeTeams         : top N seeds who skip the play-in
    // playInSlots      : semifinal match slots waiting for play-in winners
    private List<Match> playInMatches;
    private List<Match> mainBracketMatches;
    private Team[]      byeTeams;
    private List<Match> playInSlots;   // semifinal matches in main bracket
    private int         playInWinnersRounds; // WB rounds inside the play-in
    private ScoreMatrix scoreMatrix;

    public TournamentBracket(Team[] teams) {
        this(teams, TournamentType.SINGLE_ELIMINATION);
    }

    public TournamentBracket(Team[] teams, TournamentType type) {
        Match.resetIdCounter();   // always start match IDs fresh for each new bracket
        this.teams                = teams;
        this.tournamentType       = type;
        this.allMatches           = new ArrayList<>();
        this.winnerToLoserMatch   = new HashMap<>();
        this.lbWinnerAdvanceMap   = new HashMap<>();
        this.losersBracketMatches = new ArrayList<>();
        this.playInMatches        = new ArrayList<>();
        this.mainBracketMatches   = new ArrayList<>();
        this.playInSlots          = new ArrayList<>();
        this.scoreMatrix          = new ScoreMatrix(teams);

        if      (type == TournamentType.SINGLE_ELIMINATION) {
            if      (teams.length == 12) buildSingleElimination12(teams);
            else if (teams.length == 24) buildSingleElimination24(teams);
            else                         buildSingleElimination(teams);
        }
        else if (type == TournamentType.ROUND_ROBIN)                buildRoundRobin(teams);
        else if (type == TournamentType.DOUBLE_ELIMINATION) {
            if      (teams.length == 12) buildDoubleElimination12(teams);
            else if (teams.length == 24) buildDoubleElimination24(teams);
            else                         buildDoubleElimination(teams);
        }
        else if (type == TournamentType.SWISS)                      buildSwissSystem(teams);
        else if (type == TournamentType.FREE_FOR_ALL)               buildFreeForAll(teams);
        else if (type == TournamentType.PLAY_IN_DOUBLE_ELIMINATION)  buildPlayInDE(teams);
        else if (type == TournamentType.PLAY_IN_SINGLE_ELIMINATION)  buildPlayInSE(teams);
        else                                                         buildSingleElimination(teams);
    }

    public TournamentBracket(Team[] teams, String type) {
        this(teams, convertToTournamentType(type));
    }

    private static TournamentType convertToTournamentType(String type) {
        if (type == null) return TournamentType.SINGLE_ELIMINATION;
        switch (type) {
            case "Single Elimination":          return TournamentType.SINGLE_ELIMINATION;
            case "Double Elimination":          return TournamentType.DOUBLE_ELIMINATION;
            case "Round Robin":                 return TournamentType.ROUND_ROBIN;
            case "Swiss System":                return TournamentType.SWISS;
            case "Free For All":                return TournamentType.FREE_FOR_ALL;
            case "Play-In Double Elimination":  return TournamentType.PLAY_IN_DOUBLE_ELIMINATION;
            case "Play-In Single Elimination":  return TournamentType.PLAY_IN_SINGLE_ELIMINATION;
            default:                            return TournamentType.SINGLE_ELIMINATION;
        }
    }

    // =========================================================================
    // SINGLE ELIMINATION
    // =========================================================================

    private void buildSingleElimination(Team[] teams) {
        int n = teams.length;
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();

        int numRounds = (int) Math.ceil(Math.log(n) / Math.log(2));
        int fullSize  = (int) Math.pow(2, numRounds);

        // For standard power-of-2 counts (4, 8, 16, 32): fill all slots, no byes
        // For bye counts (10 → pad to 16, 20 → pad to 32): top seeds get byes
        Team[] slots = seededSlots(teams, fullSize);

        List<Match> currentRound = new ArrayList<>();
        int matchId = 1;

        for (int i = 0; i < fullSize; i += 2) {
            Team t1 = slots[i];
            Team t2 = slots[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            if (!isPowerOfTwo(n)) autoCompleteBye(m); // only auto-advance byes for non-power-of-2
            if (m.getTeam1() != null || m.getTeam2() != null) {
                currentRound.add(m);
                allMatches.add(m);
            }
        }

        int round = 2;
        while (currentRound.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i + 1 < currentRound.size(); i += 2) {
                Match parent = new Match(round);
                parent.setMatchId(matchId++);
                parent.setLeftChild(currentRound.get(i));
                parent.setRightChild(currentRound.get(i + 1));
                if (currentRound.get(i).isCompleted())
                    parent.setTeam1(currentRound.get(i).getWinner());
                if (currentRound.get(i + 1).isCompleted())
                    parent.setTeam2(currentRound.get(i + 1).getWinner());
                next.add(parent);
                allMatches.add(parent);
            }
            currentRound = next;
            round++;
        }

        this.totalRounds = round - 1;
        this.root = currentRound.isEmpty() ? null : currentRound.get(0);
    }

    private boolean isPowerOfTwo(int n) { return n > 0 && (n & (n - 1)) == 0; }

    // =========================================================================
    // SINGLE ELIMINATION – 24 teams
    //
    // 32-slot grid: top 8 seeds (Seeds #1-8) get byes → auto-advance R1.
    // Round 1  (Opening Round):  8 matches — Seeds #9-#24 (16 teams)
    // Round 2  (Round of 16):    8 matches — 8 R1-winners vs 8 bye seeds
    // Round 3  (Quarterfinals):  4 matches
    // Round 4  (Semifinals):     2 matches
    // Round 5  (Championship):   1 match
    // =========================================================================

    private void buildSingleElimination24(Team[] teams) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();

        // Seeds #1-8 are bye teams; seeds #9-24 play Round 1
        // We model this as a 32-slot bracket where the 8 bye seeds occupy the
        // "first" slot of each R1 pair and their partner slot is null (auto-bye).
        // The 16 play-in teams fill the 16 contested slots in Round 1.
        //
        // Bracket layout (32 slots, seed-ordered):
        //  slot 0  = seed 1  (bye)   slot 1  = seed 16 (R1 play)  → R1 match A
        //  slot 2  = seed 9  (R1)    slot 3  = seed 24 (R1 play)  → R1 match B
        //  ... etc.
        //
        // Simpler approach: build as a standard 32-slot bracket where
        // positions for seeds 1-8 auto-complete as byes, then rounds 2-5
        // are built normally via child-links.

        int matchId = 1;

        // seededSlots for 32 slots, but only 24 teams provided.
        // Seeds 1-24 fill slots; "seeds" 25-32 would be null → those slots vanish.
        // With 24 teams in a 32-slot bracket the seeding places byes at positions
        // that the buildSeedOrder algorithm naturally puts seeds 25-32 (non-existent),
        // but we need Seeds 1-8 to be the byes, not 25-32.
        //
        // Custom approach: we explicitly control the 32 slots.
        // The standard power-of-2 seeding for 32 slots is:
        //   1 vs 32, 16 vs 17, 9 vs 24, 8 vs 25, 5 vs 28, 12 vs 21, 13 vs 20, 4 vs 29 ...
        // For 24 teams "25-32" don't exist → those slots are null → autobye.
        // But seeds 1-8 end up paired against seeds 25-32 (non-existent) → they auto-bye! ✓
        // Seeds 9-24 all pair against each other or against existing seeds → R1 real matches. ✓

        Team[] slots32 = seededSlots(teams, 32); // null for positions where seed > 24

        // Round 1: build all 16 slot-pairs
        List<Match> r1Matches = new ArrayList<>();
        for (int i = 0; i < 32; i += 2) {
            Team t1 = slots32[i];
            Team t2 = slots32[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            autoCompleteBye(m); // auto-advance bye seeds (seeds 1-8 vs null)
            r1Matches.add(m);
            allMatches.add(m);
        }

        // Rounds 2-5: pair up previous round's matches
        List<Match> currentRound = r1Matches;
        int round = 2;
        while (currentRound.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i + 1 < currentRound.size(); i += 2) {
                Match parent = new Match(round);
                parent.setMatchId(matchId++);
                parent.setLeftChild(currentRound.get(i));
                parent.setRightChild(currentRound.get(i + 1));
                if (currentRound.get(i).isCompleted())
                    parent.setTeam1(currentRound.get(i).getWinner());
                if (currentRound.get(i + 1).isCompleted())
                    parent.setTeam2(currentRound.get(i + 1).getWinner());
                next.add(parent);
                allMatches.add(parent);
            }
            currentRound = next;
            round++;
        }

        this.totalRounds = round - 1;
        this.root = currentRound.isEmpty() ? null : currentRound.get(0);

        System.out.println("SE-24 built: totalRounds=" + totalRounds
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // SINGLE ELIMINATION – 12 teams
    //
    // 16-slot grid: top 4 seeds (Seeds #1-4) get byes → auto-advance R1.
    // Round 1 (Opening Round): 4 matches — Seeds #5-#12 (8 teams)
    // Round 2 (Quarterfinals): 4 matches — 4 R1-winners vs 4 bye seeds
    // Round 3 (Semifinals):    2 matches
    // Round 4 (Championship):  1 match
    // =========================================================================

    private void buildSingleElimination12(Team[] teams) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();

        int matchId = 1;

        // Use the 16-slot seeding trick: seeds 1-4 are paired against null (seeds 13-16)
        // → they auto-bye. Seeds 5-12 are paired against each other → real R1 matches.
        Team[] slots16 = seededSlots(teams, 16); // null where seed > 12

        List<Match> r1Matches = new ArrayList<>();
        for (int i = 0; i < 16; i += 2) {
            Team t1 = slots16[i];
            Team t2 = slots16[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            autoCompleteBye(m); // auto-advance seeds 1-4 who face null
            r1Matches.add(m);
            allMatches.add(m);
        }

        // Rounds 2-4: standard halving
        List<Match> currentRound = r1Matches;
        int round = 2;
        while (currentRound.size() > 1) {
            List<Match> next = new ArrayList<>();
            for (int i = 0; i + 1 < currentRound.size(); i += 2) {
                Match parent = new Match(round);
                parent.setMatchId(matchId++);
                parent.setLeftChild(currentRound.get(i));
                parent.setRightChild(currentRound.get(i + 1));
                if (currentRound.get(i).isCompleted())
                    parent.setTeam1(currentRound.get(i).getWinner());
                if (currentRound.get(i + 1).isCompleted())
                    parent.setTeam2(currentRound.get(i + 1).getWinner());
                next.add(parent);
                allMatches.add(parent);
            }
            currentRound = next;
            round++;
        }

        this.totalRounds = round - 1;
        this.root = currentRound.isEmpty() ? null : currentRound.get(0);

        System.out.println("SE-12 built: totalRounds=" + totalRounds
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // DOUBLE ELIMINATION – 12 teams
    //
    // Winners Bracket:
    //   WB R1: 4 matches — Seeds #5-#12 (8 teams); 4 winners advance, 4 losers → LB R1
    //   WB R2: 4 matches — 4 WB-R1 winners vs 4 bye seeds; 4 winners advance, 4 losers → LB R2
    //   WB R3 (Semifinals): 2 matches; 2 winners advance, 2 losers → LB R4
    //   WB R4 (Finals): 1 match; winner → Grand Final, loser → LB R6
    //
    // Losers Bracket (6 rounds):
    //   LB R1: 2 matches — 4 WB-R1 losers play each other
    //   LB R2: 2 matches — 2 LB-R1 survivors vs 2 of the 4 WB-R2 losers
    //   LB R3: 2 matches — 2 LB-R2 survivors vs remaining 2 WB-R2 losers
    //   LB R4: 2 matches — 2 LB-R3 survivors vs 2 WB-SF losers
    //   LB R5: 1 match  — 2 LB-R4 survivors play each other
    //   LB R6 (Losers Final): 1 match — 1 LB-R5 survivor vs 1 WB-Finals loser
    //
    // Grand Final + optional Bracket Reset.
    // =========================================================================

    private void buildDoubleElimination12(Team[] teams) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();
        lbWinnerAdvanceMap = new HashMap<>();

        int matchId       = 1;
        int losersMatchId = 1000;

        // Seeds 1-4 get WB byes; Seeds 5-12 play WB Round 1.
        // Use 16-slot seeding: seeds 1-4 paired against null → auto-bye.
        Team[] slots16 = seededSlots(teams, 16);

        // WB Round 1: 4 real matches + 4 auto-bye matches
        List<Match> wbR1All  = new ArrayList<>();
        List<Match> wbR1Real = new ArrayList<>();
        List<Match> wbR1Bye  = new ArrayList<>();

        for (int i = 0; i < 16; i += 2) {
            Team t1 = slots16[i];
            Team t2 = slots16[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            boolean isBye = autoCompleteBye(m);
            wbR1All.add(m);
            allMatches.add(m);
            if (isBye) wbR1Bye.add(m);
            else        wbR1Real.add(m);
        }

        // WB Round 2: 4 matches (real R1 winners vs bye-seed winners)
        List<Match> wbR2 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1All.size(); i += 2) {
            Match left  = wbR1All.get(i);
            Match right = wbR1All.get(i + 1);
            Match m = new Match(2);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setLeftChild(left);
            m.setRightChild(right);
            if (left.isCompleted())  m.setTeam1(left.getWinner());
            if (right.isCompleted()) m.setTeam2(right.getWinner());
            wbR2.add(m);
            allMatches.add(m);
        }

        // WB Rounds 3-4: standard halving
        List<List<Match>> wbRounds = new ArrayList<>();
        wbRounds.add(wbR1All);
        wbRounds.add(wbR2);
        List<Match> wbCurrent = wbR2;
        for (int r = 3; r <= 4; r++) {
            List<Match> wbNext = new ArrayList<>();
            for (int i = 0; i + 1 < wbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setLeftChild(wbCurrent.get(i));
                m.setRightChild(wbCurrent.get(i + 1));
                if (wbCurrent.get(i).isCompleted())   m.setTeam1(wbCurrent.get(i).getWinner());
                if (wbCurrent.get(i+1).isCompleted()) m.setTeam2(wbCurrent.get(i+1).getWinner());
                wbNext.add(m);
                allMatches.add(m);
            }
            wbRounds.add(wbNext);
            wbCurrent = wbNext;
        }
        Match wbFinal = wbCurrent.get(0); // WB R4 — 1 match

        // LB Round 1 (elim): 2 matches — 4 WB-R1 real losers play each other
        List<Match> lb1 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1Real.size(); i += 2) {
            Match lm = new Match(1);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1Real.get(i),     lm);
            winnerToLoserMatch.put(wbR1Real.get(i + 1), lm);
            lb1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 2 (drop): 2 matches — 2 lb1 survivors vs WBR2 losers[0-1]
        List<Match> lb2 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(2);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(i), lm);
            lbWinnerAdvanceMap.put(lb1.get(i),  lm);
            lb2.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 3 (drop): 2 matches — 2 lb2 survivors vs WBR2 losers[2-3]
        List<Match> lb3 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(3);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(2 + i), lm);
            lbWinnerAdvanceMap.put(lb2.get(i),       lm);
            lb3.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 4 (drop): 2 matches — 2 lb3 survivors vs 2 WB-SF (WBR3) losers
        List<Match> wbSF = wbRounds.get(2); // WB Round 3 = index 2, 2 matches
        List<Match> lb4 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(4);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbSF.get(i), lm);
            lbWinnerAdvanceMap.put(lb3.get(i),  lm);
            lb4.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 5 (elim): 1 match — 2 lb4 survivors play each other
        Match lb5 = new Match(5);
        lb5.setMatchId(losersMatchId++);
        lbWinnerAdvanceMap.put(lb4.get(0), lb5);
        lbWinnerAdvanceMap.put(lb4.get(1), lb5);
        losersBracketMatches.add(lb5);
        allMatches.add(lb5);

        // LB Round 6 (Losers Final): 1 match — lb5 survivor vs WB-Finals loser
        Match lb6 = new Match(6);
        lb6.setMatchId(losersMatchId++);
        winnerToLoserMatch.put(wbFinal, lb6);
        lbWinnerAdvanceMap.put(lb5,     lb6);
        losersBracketMatches.add(lb6);
        allMatches.add(lb6);

        // Grand Final
        Match gf = new Match(7);
        gf.setMatchId(matchId++);
        gf.setLeftChild(wbFinal);
        gf.setRightChild(lb6);
        allMatches.add(gf);

        this.grandFinals = gf;
        this.root        = gf;
        this.totalRounds = 7;

        System.out.println("DE-12 built:"
            + " WBmatches=" + wbRounds.stream().mapToInt(List::size).sum()
            + " LBmatches=" + losersBracketMatches.size()
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // DOUBLE ELIMINATION – 24 teams
    //
    // Winners Bracket (5 rounds):
    //   WB R1:  8 matches — Seeds #9-#24 (16 teams); 8 winners advance, 8 losers → LB R1
    //   WB R2:  8 matches — 8 WB-R1 winners vs 8 bye seeds; 8 winners advance, 8 losers → LB R2
    //   WB R3 (QF): 4 matches; 4 winners advance, 4 losers → LB R4
    //   WB R4 (SF): 2 matches; 2 winners advance, 2 losers → LB R6
    //   WB R5 (Finals): 1 match; winner → Grand Final, loser → LB R8
    //
    // Losers Bracket (8 rounds):
    //   LB R1: 4 matches — 8 WB-R1 losers play each other
    //   LB R2: 8 matches — 4 LB-R1 survivors + 8 WB-R2 losers  (drop round)
    //   LB R3: 4 matches — 8 LB-R2 survivors play each other (elim round)
    //   LB R4: 4 matches — 4 LB-R3 survivors + 4 WB-QF losers (drop round)
    //   LB R5: 2 matches — 4 LB-R4 survivors play each other (elim round)
    //   LB R6: 2 matches — 2 LB-R5 survivors + 2 WB-SF losers (drop round)
    //   LB R7: 1 match  — 2 LB-R6 survivors play each other (elim round)
    //   LB R8: 1 match  — 1 LB-R7 survivor + 1 WB-Finals loser (Losers Final)
    //
    // Grand Final + optional Bracket Reset.
    // =========================================================================

    private void buildDoubleElimination24(Team[] teams) {
        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();
        lbWinnerAdvanceMap = new HashMap<>();

        int matchId       = 1;
        int losersMatchId = 1000;

        // ── Seeds 1-8 get WB byes; Seeds 9-24 play WB Round 1 ──────────────
        // Use the same 32-slot seeding trick as SE-24:
        // seededSlots(teams, 32) places seeds 1-8 paired against null (→ byes),
        // and seeds 9-24 paired against each other for real R1 matches.
        Team[] slots32 = seededSlots(teams, 32);

        // ── WB Round 1: 8 real matches + 8 auto-bye matches ─────────────────
        // We keep all 16 matches (including the 8 byes) so the child-link tree
        // structure for later rounds stays intact.  The 8 bye matches are
        // auto-completed; the 8 real matches await results.
        List<Match> wbR1All = new ArrayList<>();  // all 16 slot-pair matches
        List<Match> wbR1Real = new ArrayList<>();  // the 8 contested matches
        List<Match> wbR1Bye  = new ArrayList<>();  // the 8 auto-completed byes

        for (int i = 0; i < 32; i += 2) {
            Team t1 = slots32[i];
            Team t2 = slots32[i + 1];
            if (t1 == null && t2 == null) continue;
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            boolean isBye = autoCompleteBye(m);
            wbR1All.add(m);
            allMatches.add(m);
            if (isBye) wbR1Bye.add(m);
            else        wbR1Real.add(m);
        }

        // ── WB Round 2: 8 matches (real R1 winners vs bye-seed winners) ─────
        List<Match> wbR2 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1All.size(); i += 2) {
            Match left  = wbR1All.get(i);
            Match right = wbR1All.get(i + 1);
            Match m = new Match(2);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setLeftChild(left);
            m.setRightChild(right);
            if (left.isCompleted())  m.setTeam1(left.getWinner());
            if (right.isCompleted()) m.setTeam2(right.getWinner());
            wbR2.add(m);
            allMatches.add(m);
        }

        // ── WB Rounds 3-5: standard halving ─────────────────────────────────
        List<List<Match>> wbRounds = new ArrayList<>();
        wbRounds.add(wbR1All);
        wbRounds.add(wbR2);
        List<Match> wbCurrent = wbR2;
        for (int r = 3; r <= 5; r++) {
            List<Match> wbNext = new ArrayList<>();
            for (int i = 0; i + 1 < wbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setLeftChild(wbCurrent.get(i));
                m.setRightChild(wbCurrent.get(i + 1));
                if (wbCurrent.get(i).isCompleted())     m.setTeam1(wbCurrent.get(i).getWinner());
                if (wbCurrent.get(i+1).isCompleted())   m.setTeam2(wbCurrent.get(i+1).getWinner());
                wbNext.add(m);
                allMatches.add(m);
            }
            wbRounds.add(wbNext);
            wbCurrent = wbNext;
        }
        Match wbFinal = wbCurrent.get(0); // WB R5 — 1 match

        // ── LOSERS BRACKET ───────────────────────────────────────────────────
        // LB R1: 4 matches — the 8 WB-R1 real losers play each other
        int lbRound = 1;
        List<Match> lbR1 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1Real.size(); i += 2) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1Real.get(i),     lm);
            winnerToLoserMatch.put(wbR1Real.get(i + 1), lm);
            lbR1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        // LB R2: 8 matches — 4 LB-R1 survivors (team2) + 8 WB-R2 losers (team1 via drop)
        // Each WB-R2 loser drops into a new LB-R2 match; the matching LB-R1 survivor
        // will fill team2 of that same match once it is known.
        lbRound = 2;
        List<Match> lbR2 = new ArrayList<>();
        // wbR2 has 8 matches → 8 potential losers; lbR1 has 4 matches → 4 survivors.
        // Pair: each LB-R1 match's survivor faces 2 WB-R2 losers (one per sub-pair).
        // Simpler: create 8 LB-R2 matches; pair them with lbR1 survivors 1:2.
        for (int i = 0; i < wbR2.size(); i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(i), lm); // WB-R2 loser → team1 of this LB match
            // LB-R1 survivor mapping: lbR1[i/2] → feeds team2 into lm
            lbWinnerAdvanceMap.put(lbR1.get(i / 2), lm); // will be overwritten for the 2nd entry
            lbR2.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        // Fix: each lbR1 survivor needs to go to BOTH lbR2[2i] and lbR2[2i+1]
        // lbWinnerAdvanceMap can only map one destination per key, so we need a different
        // approach. We'll store the two lbR2 matches that a given lbR1 match's winner feeds.
        // Actually the standard propagateWinnerUp uses lbWinnerAdvanceMap (1:1).
        // For the 1:2 fan-out we instead set lbR2[2i] leftChild = lbR1[i] and
        // lbR2[2i+1] leftChild = lbR1[i] as well — but a match can't have two parents.
        //
        // Correct architecture: each lbR1[i] survivor → exactly ONE lbR2 match.
        // So we create only 4 lbR2 matches (survivors), each receiving 1 lbR1 survivor
        // and 1 WB-R2 loser. But spec says 8 LB-R2 matches ("4 survivors face 8 fresh losers").
        //
        // Re-reading spec: "LB R2: 8 matches (4 survivors face the 8 fresh losers from WB R2)"
        // = 4 LB-R1 survivors + 8 WB-R2 losers = 12 participants → can't make 8 two-team matches.
        // Standard DE interpretation: drop round (8 matches) where each WB-R2 loser gets
        // its own match, and LB-R1 survivors are spread across them — but 4 vs 8 doesn't split evenly.
        //
        // Standard DE rule: LB has alternating "drop" and "elim" rounds.
        // After WB-R2 (8 losers drop): LB drop round has 8 matches.
        // Each lbR1 survivor plays a WB-R2 loser.  But there are only 4 survivors for 8 losers.
        // → Each lbR1 survivor faces TWO sequential WB-R2 losers? No — that's two separate matches.
        // The spec actually means: 4 survivors + 8 losers → combined = 12, but that's wrong for 8 two-team matches.
        //
        // CORRECT reading per user spec: "8 matches" = 4 LB-R1 survivors + 8 WB-R2 losers.
        // This is impossible as 8 two-team matches with only 12 unique teams unless some teams
        // play twice — which isn't how DE works.
        //
        // The actual standard 24-team DE structure is:
        //   LB-R1: 4 matches (8 WB-R1 losers → 4 survivors)
        //   LB-R2 DROP: 4 matches (4 LB-R1 survivors vs 4 of the 8 WB-R2 losers)  — but that leaves 4 WB-R2 losers
        //   
        // The user's spec describes 8 LB-R2 matches which requires 16 teams. That's only possible if
        // ALL 8 WB-R2 losers enter AND all 8 LB-R1 participants re-enter (impossible in DE).
        //
        // Correct interpretation: the user's "LB R2: 8 matches" is a TYPO / miscalculation.
        // The real count for a proper 24-team DE where 8 WB-R2 losers drop in is:
        //   LB-R1: 4 matches (8 WB-R1 losers → 4 survivors)
        //   LB-R2 (drop): 8 matches — NO, still 12 teams for 8 matches = impossible.
        //
        // Only consistent interpretation: LB-R2 is a pure drop round where each of the 8 WB-R2
        // losers gets paired with one of the 4 LB-R1 survivors — meaning each survivor plays TWICE
        // (once per pair). That's still not standard.
        //
        // FINAL RESOLUTION: Follow user's match counts exactly (they define the format),
        // using the standard DE alternating drop/elim architecture:
        //   LB-R1 (elim):  4 matches  (8 WB-R1 losers → 4 survivors)
        //   LB-R2 (drop):  4 matches  (4 LB-R1 survivors vs 4 of 8 WB-R2 losers) ← first half
        //   Then the other 4 WB-R2 losers need their own round → LB-R2b (4 more matches)
        //   ... This collapses into 8 total matches in what user calls "LB R2".
        //
        // SIMPLEST: Treat user's LB-R2 as two sub-rounds combined visually but sequentially:
        // Actually the cleanest and most common real-world approach for 24-team DE:
        //   LB-R1: 4 matches — 8 WB-R1 losers play each other → 4 advance
        //   LB-R2: 4 matches — 4 survivors vs 4 WB-R2 losers → 4 advance
        //   LB-R3: 4 matches — remaining 4 WB-R2 losers vs 4 LB-R2 survivors → 4 advance
        //   LB-R4: 2 matches — ...
        // But user says LB-R2 has 8 matches. Let's honour that literally.
        //
        // 8 matches with 4 LB-R1 survivors + 8 WB-R2 losers = 12 teams, 8 matches.
        // Only way: 4 survivor matches where each survivor faces a WB-R2 loser (4 matches),
        // PLUS 4 "consolation" matches with the remaining WB-R2 losers... but they're eliminated.
        // OR: user just means the 8 WB-R2 losers each get their OWN match (8 matches) and the
        // 4 survivors distribute one-per-two-matches (bracket half structure).
        //
        // FINAL FINAL: Implement exactly as user specified using a two-phase R2:
        //   Phase A (4 matches): the 4 LB-R1 survivors each face one WB-R2 loser
        //   Phase B (4 matches): the 4 Phase-A winners each face another WB-R2 loser
        // User probably means Phase-A + Phase-B = "8 matches" in "LB R2" column.
        // But that's actually LB-R2 + LB-R3 per standard naming.
        // We'll match the user's round numbering exactly (8 LB rounds as labeled).

        // ── Rebuild LB with exact user-specified structure ────────────────────
        // Discard the partial lbR2 above and rebuild from scratch correctly.
        losersBracketMatches.clear();
        // Remove LB matches from allMatches
        allMatches.removeIf(m -> m.getMatchId() >= 1000);
        lbWinnerAdvanceMap.clear();
        winnerToLoserMatch.clear();
        losersMatchId = 1000;

        // Re-register WB-R1 → LB-R1 mapping
        // LB R1: 4 matches — 8 WB-R1 real losers play each other
        lbRound = 1;
        lbR1 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1Real.size(); i += 2) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1Real.get(i),     lm);
            winnerToLoserMatch.put(wbR1Real.get(i + 1), lm);
            lbR1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        // lbR1 has 4 matches → 4 survivors

        // LB R2: 4 matches — 4 LB-R1 survivors each face 1 WB-R2 loser (first 4 of wbR2)
        lbRound = 2;
        List<Match> lbR2a = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(i), lm);      // WB-R2 loser[0-3] → this match
            lbWinnerAdvanceMap.put(lbR1.get(i), lm);       // LB-R1 survivor[i] → this match
            lbR2a.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB R3: 4 matches — 4 LB-R2 survivors each face 1 WB-R2 loser (last 4 of wbR2)
        lbRound = 3;
        List<Match> lbR3 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(4 + i), lm);  // WB-R2 loser[4-7] → this match
            lbWinnerAdvanceMap.put(lbR2a.get(i), lm);      // LB-R2 survivor[i] → this match
            lbR3.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB R4: 2 matches — 4 LB-R3 survivors play each other (elim round)
        lbRound = 4;
        List<Match> lbR4 = new ArrayList<>();
        for (int i = 0; i + 1 < lbR3.size(); i += 2) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            lbWinnerAdvanceMap.put(lbR3.get(i),     lm);
            lbWinnerAdvanceMap.put(lbR3.get(i + 1), lm);
            lbR4.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB R5: 2 matches — 2 LB-R4 survivors each face 1 WB-QF (WBR3) loser
        List<Match> wbR3 = wbRounds.get(2); // WB Round 3 = index 2 (QF, 4 matches)
        lbRound = 5;
        List<Match> lbR5 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR3.get(i * 2),     lm); // WB-QF losers pair into same LB match
            winnerToLoserMatch.put(wbR3.get(i * 2 + 1), lm);
            lbWinnerAdvanceMap.put(lbR4.get(i), lm);
            lbR5.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB R6: 1 match — 2 LB-R5 survivors play each other (elim round)
        lbRound = 6;
        Match lbR6Match = new Match(lbRound);
        lbR6Match.setMatchId(losersMatchId++);
        lbWinnerAdvanceMap.put(lbR5.get(0), lbR6Match);
        lbWinnerAdvanceMap.put(lbR5.get(1), lbR6Match);
        losersBracketMatches.add(lbR6Match);
        allMatches.add(lbR6Match);

        // LB R7: 2 matches — 1 LB-R6 survivor + 2 WB-SF (WBR4) losers
        List<Match> wbR4 = wbRounds.get(3); // WB Round 4 = SF, 2 matches
        lbRound = 7;
        List<Match> lbR7 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(lbRound);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR4.get(i), lm);
            lbR7.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        // LB-R6 survivor feeds into LB-R7[0]
        lbWinnerAdvanceMap.put(lbR6Match, lbR7.get(0));
        // LB-R7[1] needs a feed from somewhere: LB-R7[0] winner? No — they should be
        // independent matches. Actually WB-R4 has 2 losers → 2 separate LB-R7 matches.
        // LB-R6 survivor goes to one, and... there's only 1 survivor for 2 slots?
        //
        // User spec: "LB R7: 1 match (2 survivors play each other)"
        // Correcting: LB-R6 and LB-R5 context → at LB-R7 entry we have:
        //   - 1 LB-R6 survivor
        //   - 2 WB-SF losers
        // That's 3 teams for 2 matches → still doesn't work cleanly.
        //
        // Re-reading the user's spec carefully:
        //   LB R6: 2 matches (2 LB-R5 survivors vs 2 WB-SF losers)  ← "drop round"
        //   LB R7: 1 match  (2 LB-R6 survivors play each other)     ← "elim round"
        //   LB R8: 1 match  (1 LB-R7 survivor vs 1 WB-Finals loser) ← "Losers Final"
        //
        // So the correct structure is:
        //   lbR5: 2 matches — elim (the 4 LB-R4 survivors play each other → 2 advance)
        //   lbR6: 2 matches — drop (2 LB-R5 survivors vs 2 WB-SF losers)
        //   lbR7: 1 match   — elim (2 LB-R6 survivors play each other)
        //   lbR8: 1 match   — drop+final (1 LB-R7 survivor vs 1 WB-Finals loser)
        //
        // Let me also re-read LB R4 and R5:
        //   LB R4: 4 matches (4 LB-R3 survivors vs 4 WB-QF losers) ← drop
        //   LB R5: 2 matches (4 LB-R4 survivors play each other)   ← elim
        //
        // So the full corrected structure:
        //   LB R1: 4 matches — elim (8 WB-R1 losers → 4 survive)
        //   LB R2: 4 matches — drop (4 LB-R1 survivors vs 4 WB-R2 losers[0-3])   ← 4 matches
        //   LB R3: 4 matches — drop (4 LB-R2 survivors vs 4 WB-R2 losers[4-7])   ← 4 matches
        //   LB R4: 4 matches — drop (4 LB-R3 survivors vs 4 WB-QF losers)        ← 4 matches
        //   LB R5: 2 matches — elim (4 LB-R4 survivors → 2 survive)
        //   LB R6: 2 matches — drop (2 LB-R5 survivors vs 2 WB-SF losers)
        //   LB R7: 1 match   — elim (2 LB-R6 survivors → 1 survive)
        //   LB R8: 1 match   — Losers Final (1 LB-R7 survivor vs 1 WB-Finals loser)
        //
        // BUT user says: LB R2 = 8 matches, LB R3 = 4 matches, LB R4 = 4 matches...
        // User's R2 (8 matches) = our R2 (4) + our R3 (4) combined into one "round"?
        // No — the user explicitly lists separate round counts.
        //
        // Let me try yet another reading:
        //   LB R1: 4 matches  (8 WBR1 losers → 4 survive)
        //   LB R2: 8 matches  (4 survive + 8 WBR2 losers) → if this is purely a "drop" round,
        //                      then we need to pair each of the 4 survivors with 2 WBR2 losers
        //                      sequentially. That means each survivor plays twice? Still weird.
        //
        // OR maybe user means: 4 survivors play all 8 losers round-robin style? No.
        //
        // The ONLY way to have 8 two-team matches in LB-R2 with 4 survivors + 8 losers:
        //   Split the 4 survivors into 4 pairs and pair each survivor with 2 WBR2 losers → 8 matches
        //   i.e., survivor[0] plays wbR2loser[0] AND survivor[0] plays wbR2loser[1]? = 2 matches from survivor[0]
        //   But a player plays only one match per round in SE/DE.
        //
        // CONCLUSION: The user's specification has an arithmetic inconsistency in LB-R2.
        // The closest VALID structure that honours all other round counts is:
        //   LB R1: 4 matches  (8 WBR1 losers, elim)
        //   LB R2: 4 matches  (4 LBR1 survivors vs 4 of 8 WBR2 losers, drop)   ← user says 8
        //   LB R3: 4 matches  (4 LBR2 survivors vs 4 remaining WBR2 losers, drop)
        //   LB R4: 4 matches  (4 LBR3 survivors vs 4 WB-QF losers, drop)
        //   LB R5: 2 matches  (4 LBR4 survivors, elim)
        //   LB R6: 2 matches  (2 LBR5 survivors vs 2 WB-SF losers, drop)
        //   LB R7: 1 match    (2 LBR6 survivors, elim)
        //   LB R8: 1 match    (1 LBR7 survivor vs 1 WB-Finals loser, Losers Final)
        //
        // This gives 4+4+4+4+2+2+1+1 = 22 LB matches total, which is correct for 24-team DE.
        // We'll label them LB R1–R8 as the user wants. LB R2 will be 4 matches even though
        // user wrote 8 — the bracket math demands it. We'll use the user's round labels.

        // Rebuild everything cleanly with the correct architecture:
        losersBracketMatches.clear();
        allMatches.removeIf(m -> m.getMatchId() >= 1000);
        lbWinnerAdvanceMap.clear();
        winnerToLoserMatch.clear();
        losersMatchId = 1000;

        // LB Round 1 (elim): 4 matches — 8 WBR1 real losers play each other
        List<Match> lb1 = new ArrayList<>();
        for (int i = 0; i + 1 < wbR1Real.size(); i += 2) {
            Match lm = new Match(1);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1Real.get(i),     lm);
            winnerToLoserMatch.put(wbR1Real.get(i + 1), lm);
            lb1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 2 (drop): 4 matches — 4 lb1 survivors vs WBR2 losers[0-3]
        List<Match> lb2 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Match lm = new Match(2);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(i), lm);
            lbWinnerAdvanceMap.put(lb1.get(i),  lm);
            lb2.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 3 (drop): 4 matches — 4 lb2 survivors vs WBR2 losers[4-7]
        List<Match> lb3 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Match lm = new Match(3);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR2.get(4 + i), lm);
            lbWinnerAdvanceMap.put(lb2.get(i),       lm);
            lb3.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 4 (drop): 4 matches — 4 lb3 survivors vs 4 WBR3 (QF) losers
        List<Match> wbQF = wbRounds.get(2);  // WB Round 3, 4 matches
        List<Match> lb4 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Match lm = new Match(4);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbQF.get(i), lm);
            lbWinnerAdvanceMap.put(lb3.get(i),  lm);
            lb4.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 5 (elim): 2 matches — 4 lb4 survivors play each other
        List<Match> lb5 = new ArrayList<>();
        for (int i = 0; i + 1 < lb4.size(); i += 2) {
            Match lm = new Match(5);
            lm.setMatchId(losersMatchId++);
            lbWinnerAdvanceMap.put(lb4.get(i),     lm);
            lbWinnerAdvanceMap.put(lb4.get(i + 1), lm);
            lb5.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 6 (drop): 2 matches — 2 lb5 survivors vs 2 WBR4 (SF) losers
        List<Match> wbSF = wbRounds.get(3);  // WB Round 4, 2 matches
        List<Match> lb6 = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Match lm = new Match(6);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbSF.get(i), lm);
            lbWinnerAdvanceMap.put(lb5.get(i),  lm);
            lb6.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }

        // LB Round 7 (elim): 1 match — 2 lb6 survivors play each other
        Match lb7 = new Match(7);
        lb7.setMatchId(losersMatchId++);
        lbWinnerAdvanceMap.put(lb6.get(0), lb7);
        lbWinnerAdvanceMap.put(lb6.get(1), lb7);
        losersBracketMatches.add(lb7);
        allMatches.add(lb7);

        // LB Round 8 (Losers Final): 1 match — lb7 survivor vs WBR5 (Finals) loser
        Match lb8 = new Match(8);
        lb8.setMatchId(losersMatchId++);
        winnerToLoserMatch.put(wbFinal, lb8);
        lbWinnerAdvanceMap.put(lb7,     lb8);
        losersBracketMatches.add(lb8);
        allMatches.add(lb8);

        // ── GRAND FINAL ──────────────────────────────────────────────────────
        Match gf = new Match(9);
        gf.setMatchId(matchId++);
        gf.setLeftChild(wbFinal);
        gf.setRightChild(lb8);
        allMatches.add(gf);

        this.grandFinals = gf;
        this.root        = gf;
        this.totalRounds = 9;

        System.out.println("DE-24 built:"
            + " WBmatches=" + wbRounds.stream().mapToInt(List::size).sum()
            + " LBmatches=" + losersBracketMatches.size()
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // DOUBLE ELIMINATION  (power-of-2 only: 4, 8, 16, 32)
    // =========================================================================

    private void buildDoubleElimination(Team[] teams) {
        int n         = teams.length;
        int numRounds = (int)(Math.log(n) / Math.log(2));
        int fullSize  = n;

        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        winnerToLoserMatch.clear();
        lbWinnerAdvanceMap = new HashMap<>();

        int matchId       = 1;
        int losersMatchId = 1000;

        Team[] slots = seededSlots(teams, fullSize);

        // WB Round 1
        List<Match> wbR1 = new ArrayList<>();
        for (int i = 0; i < fullSize; i += 2) {
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setTeam1(slots[i]);
            m.setTeam2(slots[i + 1]);
            wbR1.add(m);
            allMatches.add(m);
        }

        List<List<Match>> wbRounds = new ArrayList<>();
        wbRounds.add(wbR1);
        List<Match> wbCurrent = new ArrayList<>(wbR1);

        for (int r = 2; r <= numRounds; r++) {
            List<Match> wbNext = new ArrayList<>();
            for (int i = 0; i + 1 < wbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setLeftChild(wbCurrent.get(i));
                m.setRightChild(wbCurrent.get(i + 1));
                wbNext.add(m);
                allMatches.add(m);
            }
            wbRounds.add(wbNext);
            wbCurrent = wbNext;
        }
        Match wbFinal = wbCurrent.get(0);

        int lbRoundNum = numRounds;
        Map<Integer, List<Match>> lbRoundMap = new LinkedHashMap<>();

        lbRoundNum++;
        List<Match> lbElim1 = new ArrayList<>();
        List<Match> wbR1List = wbRounds.get(0);
        for (int i = 0; i + 1 < wbR1List.size(); i += 2) {
            Match lm = new Match(lbRoundNum);
            lm.setMatchId(losersMatchId++);
            winnerToLoserMatch.put(wbR1List.get(i),     lm);
            winnerToLoserMatch.put(wbR1List.get(i + 1), lm);
            lbElim1.add(lm);
            losersBracketMatches.add(lm);
            allMatches.add(lm);
        }
        lbRoundMap.put(lbRoundNum, lbElim1);

        List<Match> lbSurvivors = new ArrayList<>(lbElim1);

        for (int wr = 2; wr <= numRounds; wr++) {
            List<Match> wbThisRound = wbRounds.get(wr - 1);

            lbRoundNum++;
            List<Match> dropRound = new ArrayList<>();
            for (int i = 0; i < wbThisRound.size() && i < lbSurvivors.size(); i++) {
                Match lm = new Match(lbRoundNum);
                lm.setMatchId(losersMatchId++);
                lm.setLeftChild(lbSurvivors.get(i));
                winnerToLoserMatch.put(wbThisRound.get(i), lm);
                // Wire: winner of lbSurvivors[i] advances into lm as team2
                lbWinnerAdvanceMap.put(lbSurvivors.get(i), lm);
                dropRound.add(lm);
                losersBracketMatches.add(lm);
                allMatches.add(lm);
            }
            lbRoundMap.put(lbRoundNum, dropRound);

            if (dropRound.size() > 1) {
                lbRoundNum++;
                List<Match> elimRound = new ArrayList<>();
                for (int i = 0; i + 1 < dropRound.size(); i += 2) {
                    Match lm = new Match(lbRoundNum);
                    lm.setMatchId(losersMatchId++);
                    lm.setLeftChild(dropRound.get(i));
                    lm.setRightChild(dropRound.get(i + 1));
                    // Wire: winners of dropRound matches advance into lm
                    lbWinnerAdvanceMap.put(dropRound.get(i),     lm);
                    lbWinnerAdvanceMap.put(dropRound.get(i + 1), lm);
                    elimRound.add(lm);
                    losersBracketMatches.add(lm);
                    allMatches.add(lm);
                }
                lbRoundMap.put(lbRoundNum, elimRound);
                lbSurvivors = elimRound;
            } else {
                lbSurvivors = dropRound;
            }
        }

        Match lbFinal = lbSurvivors.isEmpty() ? null : lbSurvivors.get(0);

        lbRoundNum++;
        grandFinals = new Match(lbRoundNum);
        grandFinals.setMatchId(matchId);
        grandFinals.setLeftChild(wbFinal);
        if (lbFinal != null) grandFinals.setRightChild(lbFinal);
        allMatches.add(grandFinals);
        this.root        = grandFinals;
        this.totalRounds = lbRoundNum;

        System.out.println("DE built: n=" + n + " WBr=" + numRounds
            + " LBmatches=" + losersBracketMatches.size()
            + " total=" + allMatches.size());
    }

    private void buildPlayInDE(Team[] teams) {
        int n = teams.length;
        int numByeTeams;
        if      (n == 6)  numByeTeams = 2;
        else if (n == 10) numByeTeams = 2;
        else if (n == 12) numByeTeams = 4;
        else if (n == 20) numByeTeams = 4;
        else if (n == 24) numByeTeams = 8;
        else              numByeTeams = (n == 10) ? 2 : 4; 
        int playInSize   = n - numByeTeams;       

        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();

        // Seeds 1-4 (index 0-3) get byes
        byeTeams = Arrays.copyOf(teams, numByeTeams);

        // Seeds 5-N (index 4-N-1) enter the DE play-in
        Team[] playInTeams = Arrays.copyOfRange(teams, numByeTeams, n);

        int matchId       = 1;
        int losersMatchId = 1000;

        // ── BUILD DE PLAY-IN ─────────────────────────────────────────────
        // Standard DE for playInTeams (power-of-2: 8 or 16)
        int piN      = playInTeams.length;
        int piRounds = (int)(Math.log(piN) / Math.log(2));
        Team[] piSlots = seededSlots(playInTeams, piN);

        // Play-In WB Round 1
        List<Match> piWbR1 = new ArrayList<>();
        for (int i = 0; i < piN; i += 2) {
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setIsPlayIn(true);
            m.setTeam1(piSlots[i]);
            m.setTeam2(piSlots[i + 1]);
            piWbR1.add(m);
            playInMatches.add(m);
            allMatches.add(m);
        }

        List<List<Match>> piWbRounds = new ArrayList<>();
        piWbRounds.add(piWbR1);
        List<Match> piWbCurrent = new ArrayList<>(piWbR1);

        for (int r = 2; r <= piRounds; r++) {
            List<Match> piWbNext = new ArrayList<>();
            for (int i = 0; i + 1 < piWbCurrent.size(); i += 2) {
                Match m = new Match(r);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setIsPlayIn(true);
                m.setLeftChild(piWbCurrent.get(i));
                m.setRightChild(piWbCurrent.get(i + 1));
                piWbNext.add(m);
                playInMatches.add(m);
                allMatches.add(m);
            }
            piWbRounds.add(piWbNext);
            piWbCurrent = piWbNext;
        }
        Match piWbFinal = piWbCurrent.get(0);
        this.playInWinnersRounds = piRounds;

        // Play-In Losers Bracket
        int lbRoundNum = piRounds;

        lbRoundNum++;
        List<Match> piLbElim1 = new ArrayList<>();
        List<Match> piWbR1List = piWbRounds.get(0);
        for (int i = 0; i + 1 < piWbR1List.size(); i += 2) {
            Match lm = new Match(lbRoundNum);
            lm.setMatchId(losersMatchId++);
            lm.setIsPlayIn(true);
            winnerToLoserMatch.put(piWbR1List.get(i),     lm);
            winnerToLoserMatch.put(piWbR1List.get(i + 1), lm);
            piLbElim1.add(lm);
            losersBracketMatches.add(lm);
            playInMatches.add(lm);
            allMatches.add(lm);
        }

        List<Match> piLbSurvivors = new ArrayList<>(piLbElim1);

        for (int wr = 2; wr <= piRounds; wr++) {
            List<Match> wbThisRound = piWbRounds.get(wr - 1);

            lbRoundNum++;
            List<Match> dropRound = new ArrayList<>();
            for (int i = 0; i < wbThisRound.size() && i < piLbSurvivors.size(); i++) {
                Match lm = new Match(lbRoundNum);
                lm.setMatchId(losersMatchId++);
                lm.setIsPlayIn(true);
                lm.setLeftChild(piLbSurvivors.get(i));
                winnerToLoserMatch.put(wbThisRound.get(i), lm);
                dropRound.add(lm);
                losersBracketMatches.add(lm);
                playInMatches.add(lm);
                allMatches.add(lm);
            }

            if (dropRound.size() > 1) {
                lbRoundNum++;
                List<Match> elimRound = new ArrayList<>();
                for (int i = 0; i + 1 < dropRound.size(); i += 2) {
                    Match lm = new Match(lbRoundNum);
                    lm.setMatchId(losersMatchId++);
                    lm.setIsPlayIn(true);
                    lm.setLeftChild(dropRound.get(i));
                    lm.setRightChild(dropRound.get(i + 1));
                    elimRound.add(lm);
                    losersBracketMatches.add(lm);
                    playInMatches.add(lm);
                    allMatches.add(lm);
                }
                piLbSurvivors = elimRound;
            } else {
                piLbSurvivors = dropRound;
            }
        }
        Match piLbFinal = piLbSurvivors.isEmpty() ? null : piLbSurvivors.get(0);

        // Play-In Grand Final: WB finalist vs LB finalist
        lbRoundNum++;
        Match piGrandFinal = new Match(lbRoundNum);
        piGrandFinal.setMatchId(losersMatchId++);
        piGrandFinal.setIsPlayIn(true);
        piGrandFinal.setIsPlayInGrandFinal(true);
        piGrandFinal.setLeftChild(piWbFinal);
        if (piLbFinal != null) piGrandFinal.setRightChild(piLbFinal);
        playInMatches.add(piGrandFinal);
        allMatches.add(piGrandFinal);

        // ── BUILD MAIN BRACKET ───────────────────────────────────────────────
        // Main bracket is a standard SE with (numByeTeams * 2) slots:
        //   numByeTeams bye seeds (pre-seeded as team1) + numByeTeams play-in survivors (fill team2)
        // For 10-team (2 byes): 4-slot SE → 2 semis → 1 final
        // For 20-team (4 byes): 8-slot SE → 4 semis → 2 FF → 1 champ

        int mainRoundBase = lbRoundNum + 1;
        int mainSize      = numByeTeams * 2;   // 4 or 8

        // Build round-1 main bracket matches — each pairs 1 bye seed vs 1 PI survivor (TBD)
        // Seeding: top seed faces weakest survivor (reverse pairing)
        List<Match> mainCurrent = new ArrayList<>();
        playInSlots = new ArrayList<>();

        // byePairing maps match index → bye seed index (best seed vs worst survivor)
        // For 2 byes: match0 = seed1 vs PI-survivor, match1 = seed2 vs PI-survivor
        // For 4 byes: match0 = seed1, match1 = seed4, match2 = seed2, match3 = seed3
        // For 8 byes: match0=s1, match1=s8, match2=s4, match3=s5, match4=s2, match5=s7, match6=s3, match7=s6
        int[] byePairing;
        if (numByeTeams == 2) {
            byePairing = new int[]{0, 1};
        } else if (numByeTeams == 4) {
            byePairing = new int[]{0, 3, 1, 2};
        } else if (numByeTeams == 8) {
            byePairing = new int[]{0, 7, 3, 4, 1, 6, 2, 5};
        } else {
            byePairing = new int[numByeTeams];
            for (int i = 0; i < numByeTeams; i++) byePairing[i] = i;
        }

        for (int i = 0; i < numByeTeams; i++) {
            Match sf = new Match(mainRoundBase);
            sf.setMatchId(matchId++);
            sf.setIsMainBracket(true);
            sf.setTeam1(byeTeams[byePairing[i]]); // bye seed waits here
            // team2 filled by play-in survivor
            mainCurrent.add(sf);
            playInSlots.add(sf);
            mainBracketMatches.add(sf);
            allMatches.add(sf);
        }

        // Wire play-in survivors → main bracket round-1 slots
        // For 2-bye: piGrandFinal → slot0, piLbFinal → slot1
        // For 4-bye: piGrandFinal → slot0, piLbFinal → slot1, + 2 more from LB semis
        // For 8-bye: piGrandFinal → slot0, piLbFinal → slot1, + 6 more from LB brackets
        piGrandFinal.setMainBracketSlot(mainCurrent.get(0));
        if (piLbFinal != null && mainCurrent.size() > 1)
            piLbFinal.setMainBracketSlot(mainCurrent.get(1));

        if (numByeTeams >= 4) {
            // Wire additional LB exits → remaining slots
            wireAdditionalPlayInSurvivorSlots(piWbRounds, piLbSurvivors, mainCurrent, piGrandFinal, piLbFinal);
        }

        // Build remaining main bracket rounds (standard SE from mainCurrent)
        int mainRound = mainRoundBase + 1;
        while (mainCurrent.size() > 1) {
            List<Match> mainNext = new ArrayList<>();
            for (int i = 0; i + 1 < mainCurrent.size(); i += 2) {
                Match m = new Match(mainRound);
                m.setMatchId(matchId++);
                m.setIsMainBracket(true);
                m.setLeftChild(mainCurrent.get(i));
                m.setRightChild(mainCurrent.get(i + 1));
                if (mainCurrent.get(i).isCompleted())
                    m.setTeam1(mainCurrent.get(i).getWinner());
                if (mainCurrent.get(i + 1).isCompleted())
                    m.setTeam2(mainCurrent.get(i + 1).getWinner());
                mainNext.add(m);
                mainBracketMatches.add(m);
                allMatches.add(m);
            }
            mainCurrent = mainNext;
            mainRound++;
        }

        grandFinals = mainCurrent.isEmpty() ? null : mainCurrent.get(0);
        this.root        = grandFinals;
        this.totalRounds = mainRound - 1;

        System.out.println("Play-In DE built: n=" + n
            + " byeTeams=" + numByeTeams
            + " playInSize=" + playInSize
            + " mainSize=" + mainSize
            + " playInMatches=" + playInMatches.size()
            + " mainMatches=" + mainBracketMatches.size()
            + " total=" + allMatches.size());
    }

    /**
     * Wires the remaining play-in survivor slots (mainR1[2] and mainR1[3])
     * to the correct play-in bracket exits. Only called for the 4-bye (20-team) case.
     *
     * For a 16-team play-in we need 4 survivors:
     *   - piWbFinal winner  (slot 0) ← already wired
     *   - piLbFinal winner  (slot 1) ← already wired
     *   - LB semi exits     (slots 2, 3) ← wired here
     */
    private void wireAdditionalPlayInSurvivorSlots(
            List<List<Match>> piWbRounds,
            List<Match> finalLbSurvivors,
            List<Match> semiFinals,
            Match piGrandFinal,
            Match piLbFinal) {

        // For an 8-team play-in (3 WB rounds), there are exactly 2 exits:
        // WB final winner + LB final winner → fills slots 0 and 1.
        // Slots 2 and 3 need to come from the SEMI-final round of the play-in.

        // Collect all play-in LB matches sorted by round descending
        List<Match> lbSorted = new ArrayList<>(losersBracketMatches);
        lbSorted.removeIf(m -> !m.isPlayIn());
        lbSorted.sort((a, b) -> Integer.compare(b.getRound(), a.getRound()));

        // The LB final is the first; the LB semi-finals are the next tier
        // We need 2 more survivors for semiFinals[2] and semiFinals[3]
        // These come from the LB one round before the LB final
        if (semiFinals.size() >= 4) {
            // Find LB matches that feed into piLbFinal
            List<Match> lbSemiSlots = new ArrayList<>();
            for (Match lm : lbSorted) {
                if (lm == piLbFinal) continue;
                if (piLbFinal != null) {
                    if (lm == piLbFinal.getLeftChild() || lm == piLbFinal.getRightChild()) {
                        lbSemiSlots.add(lm);
                    }
                }
            }

            // For 16-team play-in there are 2 LB semi matches → feed slots 2 and 3
            if (lbSemiSlots.size() >= 2) {
                lbSemiSlots.get(0).setMainBracketSlot(semiFinals.get(2));
                lbSemiSlots.get(1).setMainBracketSlot(semiFinals.get(3));
            } else if (lbSemiSlots.size() == 1) {
                // 8-team play-in: only 2 true survivors; use WB semifinal exits
                List<Match> wbSemis = piWbRounds.size() >= 2
                        ? piWbRounds.get(piWbRounds.size() - 2) : new ArrayList<>();
                if (wbSemis.size() >= 2) {
                    wbSemis.get(0).setMainBracketSlot(semiFinals.get(2));
                    wbSemis.get(1).setMainBracketSlot(semiFinals.get(3));
                } else if (wbSemis.size() == 1) {
                    wbSemis.get(0).setMainBracketSlot(semiFinals.get(2));
                    lbSemiSlots.get(0).setMainBracketSlot(semiFinals.get(3));
                }
            }
        }
    }

    // =========================================================================
    // PLAY-IN SINGLE ELIMINATION
    //
    //  Odd/non-power-of-2 team counts (12, 20, 24) use a play-in stage to
    //  reduce to the nearest power-of-2, then run a standard SE main bracket.
    //
    //  12 teams → next power-of-2 below is 8.  4 teams play-in (2 matches),
    //             4 winners join 8 bye seeds for an 8-team SE main bracket.
    //             Actually: 12 = 8 + 4 extra.  We give 4 byes to top seeds,
    //             play-in the bottom 8 to produce 4 survivors → 8-team SE.
    //
    //  20 teams → 16-team main bracket.  4 extra teams need a play-in.
    //             Top 16 get byes; bottom 4 play-in to produce 2 survivors
    //             who fill the last 2 slots of a 16-team SE bracket.
    //             Simpler: top 12 bye, bottom 8 play-in → 4 survivors → 16-team SE.
    //
    //  24 teams → 16-team main bracket.  Top 8 bye, bottom 16 play-in → 8 survivors.
    //
    //  General rule:
    //    mainSize  = largest power-of-2 ≤ n that satisfies mainSize >= n/2
    //    byeCount  = mainSize - (n - mainSize)   [seeds that skip play-in]
    //    playInCount = n - byeCount              [teams that must play-in]
    //    survivors = mainSize - byeCount         [play-in winners needed]
    //    playInRounds = log2(playInCount/survivors)  [SE rounds in play-in]
    // =========================================================================

    private void buildPlayInSE(Team[] teams) {
        int n = teams.length;

        // Special cases for 10-team and 20-team play-in SE:
        //   10 teams → top 2 bye, 8 play-in → 2 survivors → 4-team main (Semis + Final)
        //   20 teams → top 4 bye, 16 play-in → 4 survivors → 8-team main (QF + Semis + Final)
        int mainSize, byeCount, playInCount, survivors;
        if (n == 10) {
            mainSize    = 4;
            byeCount    = 2;
            playInCount = 8;
            survivors   = 2;
        } else if (n == 12) {
            // Top 4 seeds get byes; Seeds 5-12 (8 teams) play-in → 4 winners
            // → 8-team main bracket (Quarterfinals + Semifinals + Final)
            mainSize    = 8;
            byeCount    = 4;
            playInCount = 8;
            survivors   = 4;
        } else if (n == 20) {
            mainSize    = 8;
            byeCount    = 4;
            playInCount = 16;
            survivors   = 4;
        } else if (n == 24) {
            // Top 8 seeds get byes; Seeds 9-24 (16 teams) play-in → 8 winners
            // → 16-team main bracket (Round of 16 + QF + SF + Final)
            mainSize    = 16;
            byeCount    = 8;
            playInCount = 16;
            survivors   = 8;
        } else {
            // General formula: smallest power-of-2 >= ceil(n/2)
            mainSize = 1;
            while (mainSize < (int) Math.ceil(n / 2.0)) mainSize <<= 1;
            survivors   = mainSize - (n - mainSize); // 2*mainSize - n
            byeCount    = survivors;
            playInCount = n - byeCount;
        }

        allMatches.clear();
        losersBracketMatches = new ArrayList<>();
        playInMatches        = new ArrayList<>();
        mainBracketMatches   = new ArrayList<>();
        playInSlots          = new ArrayList<>();
        winnerToLoserMatch.clear();

        byeTeams = Arrays.copyOf(teams, byeCount);
        Team[] playInTeams = Arrays.copyOfRange(teams, byeCount, n);

        int matchId = 1;

        // ── PLAY-IN: standard single elimination ────────────────────────────
        // playInTeams.length is always a power-of-2 and == survivors * 2
        Team[] piSlots = seededSlots(playInTeams, playInTeams.length);

        List<Match> piCurrent = new ArrayList<>();
        for (int i = 0; i < playInTeams.length; i += 2) {
            Match m = new Match(1);
            m.setMatchId(matchId++);
            m.setIsWinnersBracket(true);
            m.setIsPlayIn(true);
            m.setTeam1(piSlots[i]);
            m.setTeam2(piSlots[i + 1]);
            piCurrent.add(m);
            playInMatches.add(m);
            allMatches.add(m);
        }

        int piRound = 2;
        while (piCurrent.size() > survivors) {
            List<Match> piNext = new ArrayList<>();
            for (int i = 0; i + 1 < piCurrent.size(); i += 2) {
                Match m = new Match(piRound);
                m.setMatchId(matchId++);
                m.setIsWinnersBracket(true);
                m.setIsPlayIn(true);
                m.setLeftChild(piCurrent.get(i));
                m.setRightChild(piCurrent.get(i + 1));
                piNext.add(m);
                playInMatches.add(m);
                allMatches.add(m);
            }
            piCurrent = piNext;
            piRound++;
        }
        // piCurrent now holds exactly 'survivors' play-in final matches

        int piRoundsUsed = piRound - 1;

        // ── MAIN BRACKET ────────────────────────────────────────────────────
        // Slots: bye seeds first, then play-in survivors
        // We build a seeded 'mainSize'-slot array:
        //   slots[0..byeCount-1]   = bye seeds (top seeds)
        //   slots[byeCount..]      = null (filled by play-in survivors later)
        Team[] mainSlotTeams = new Team[mainSize];
        for (int i = 0; i < byeCount; i++) mainSlotTeams[i] = byeTeams[i];
        // play-in survivor slots remain null

        // Use seeded bracket ordering for mainSize
        int[] seedOrder = buildSeedOrder(mainSize);
        // Map seed position → mainSlotTeam (null = play-in survivor TBD)
        Team[] orderedSlots = new Team[mainSize];
        for (int i = 0; i < mainSize; i++) {
            int s = seedOrder[i];
            orderedSlots[i] = (s <= mainSize && s - 1 < mainSlotTeams.length)
                               ? mainSlotTeams[s - 1] : null;
        }

        // Collect which ordered-slot indices are null (play-in survivor slots)
        List<Integer> piSlotIndices = new ArrayList<>();
        for (int i = 0; i < mainSize; i++) {
            if (orderedSlots[i] == null) piSlotIndices.add(i);
        }

        int mainRound = piRoundsUsed + 1;

        // Build main bracket round 1 matches
        List<Match> mainCurrent = new ArrayList<>();
        List<Match> mainR1 = new ArrayList<>();
        // We track which R1 matches have a play-in survivor slot
        Map<Integer, Match> piSlotIndexToMatch = new HashMap<>();

        for (int i = 0; i < mainSize; i += 2) {
            Match m = new Match(mainRound);
            m.setMatchId(matchId++);
            m.setIsMainBracket(true);
            Team t1 = orderedSlots[i];
            Team t2 = orderedSlots[i + 1];
            if (t1 != null) m.setTeam1(t1);
            if (t2 != null) m.setTeam2(t2);
            // Track null slots for wiring play-in survivors
            if (t1 == null) piSlotIndexToMatch.put(i, m);     // team1 slot
            if (t2 == null) piSlotIndexToMatch.put(i + 1, m); // team2 slot
            // Do NOT autoCompleteBye here — team2 slot is still waiting for a play-in survivor
            mainCurrent.add(m);
            mainR1.add(m);
            mainBracketMatches.add(m);
            allMatches.add(m);
        }
        mainRound++;

        // Wire play-in survivor matches → main bracket R1 slots
        // piSlotIndices[k] → piCurrent[k] (kth survivor match fills kth null slot)
        for (int k = 0; k < piSlotIndices.size() && k < piCurrent.size(); k++) {
            int slotIdx = piSlotIndices.get(k);
            Match piWinner = piCurrent.get(k);
            Match target = piSlotIndexToMatch.get(slotIdx);
            if (target != null) {
                piWinner.setMainBracketSlot(target);
                // Mark which "team" slot (1 or 2) this survivor fills
                // We store it as team1 or team2 being null
                playInSlots.add(target);
            }
        }

        // Build remaining main bracket rounds
        while (mainCurrent.size() > 1) {
            List<Match> mainNext = new ArrayList<>();
            for (int i = 0; i + 1 < mainCurrent.size(); i += 2) {
                Match m = new Match(mainRound);
                m.setMatchId(matchId++);
                m.setIsMainBracket(true);
                m.setLeftChild(mainCurrent.get(i));
                m.setRightChild(mainCurrent.get(i + 1));
                if (mainCurrent.get(i).isCompleted())
                    m.setTeam1(mainCurrent.get(i).getWinner());
                if (mainCurrent.get(i + 1).isCompleted())
                    m.setTeam2(mainCurrent.get(i + 1).getWinner());
                // Do NOT autoCompleteBye — both slots must be earned, not auto-advanced
                mainNext.add(m);
                mainBracketMatches.add(m);
                allMatches.add(m);
            }
            mainCurrent = mainNext;
            mainRound++;
        }

        grandFinals = mainCurrent.isEmpty() ? null : mainCurrent.get(0);
        this.root        = grandFinals;
        this.totalRounds = mainRound - 1;

        System.out.println("Play-In SE built: n=" + n
            + " byeCount=" + byeCount
            + " playInCount=" + playInCount
            + " survivors=" + survivors
            + " mainSize=" + mainSize
            + " playInMatches=" + playInMatches.size()
            + " mainMatches=" + mainBracketMatches.size()
            + " total=" + allMatches.size());
    }

    // =========================================================================
    // ROUND ROBIN
    // =========================================================================

    private void buildRoundRobin(Team[] teams) {
        this.totalRounds = 1;
        allMatches.clear();
        for (int i = 0; i < teams.length; i++)
            for (int j = i + 1; j < teams.length; j++) {
                Match m = new Match(1);
                m.setTeam1(teams[i]);
                m.setTeam2(teams[j]);
                allMatches.add(m);
            }
        this.root = null;
    }

    // =========================================================================
    // SWISS SYSTEM
    // =========================================================================

    private void buildSwissSystem(Team[] teams) {
        this.totalRounds = Math.min(5, teams.length / 2);
        allMatches.clear();
        List<Team> shuffled = new ArrayList<>(Arrays.asList(teams));
        Collections.shuffle(shuffled);
        for (int r = 1; r <= totalRounds; r++) {
            for (int i = 0; i + 1 < shuffled.size(); i += 2) {
                Match m = new Match(r);
                m.setTeam1(shuffled.get(i));
                m.setTeam2(shuffled.get(i + 1));
                allMatches.add(m);
            }
            Collections.shuffle(shuffled);
        }
        this.root = null;
    }

    // =========================================================================
    // FREE FOR ALL
    // =========================================================================

    private void buildFreeForAll(Team[] teams) {
        // FFA = full round-robin schedule (every team plays every other team once),
        // distributed across (n-1) rounds using the standard circle/polygon algorithm.
        // Requires at least 4 teams.
        int n = teams.length;
        allMatches.clear();

        // If n is odd, add a dummy null slot so the circle algorithm works cleanly
        // (the match involving null is simply skipped).
        int size = (n % 2 == 0) ? n : n + 1;
        Team[] circle = new Team[size];
        for (int i = 0; i < n; i++) circle[i] = teams[i];
        // circle[size-1] == null for odd n

        int numRounds = size - 1;
        this.totalRounds = numRounds;

        for (int round = 1; round <= numRounds; round++) {
            // Pair position 0 with position (size/2), then 1 with (size-1), 2 with (size-2), …
            for (int i = 0; i < size / 2; i++) {
                Team t1 = circle[i];
                Team t2 = circle[size - 1 - i];
                if (t1 != null && t2 != null) {
                    Match m = new Match(round);
                    m.setTeam1(t1);
                    m.setTeam2(t2);
                    allMatches.add(m);
                }
            }
            // Rotate: keep circle[0] fixed, rotate the rest one step clockwise
            Team last = circle[size - 1];
            for (int i = size - 1; i > 1; i--) circle[i] = circle[i - 1];
            circle[1] = last;
        }

        this.root = null;
    }

    // =========================================================================
    // SEEDING HELPERS
    // =========================================================================

    private Team[] seededSlots(Team[] teams, int bracketSize) {
        int[] seedInSlot = buildSeedOrder(bracketSize);
        Team[] slots = new Team[bracketSize];
        for (int i = 0; i < bracketSize; i++) {
            int seedNum = seedInSlot[i];
            if (seedNum <= teams.length) slots[i] = teams[seedNum - 1];
        }
        return slots;
    }

    private int[] buildSeedOrder(int size) {
        List<Integer> result = buildSeedOrderList(size);
        int[] arr = new int[size];
        for (int i = 0; i < size; i++) arr[i] = result.get(i);
        return arr;
    }

    private List<Integer> buildSeedOrderList(int n) {
        if (n == 1) {
            List<Integer> base = new ArrayList<>();
            base.add(1);
            return base;
        }
        List<Integer> half = buildSeedOrderList(n / 2);
        List<Integer> result = new ArrayList<>();
        for (int h : half) {
            result.add(h);
            result.add(n + 1 - h);
        }
        return result;
    }

    private boolean autoCompleteBye(Match m) {
        if (m.getTeam1() != null && m.getTeam2() == null) {
            m.setWinner(m.getTeam1(), 1, 0);
            return true;
        }
        if (m.getTeam1() == null && m.getTeam2() != null) {
            m.setWinner(m.getTeam2(), 0, 1);
            return true;
        }
        return false;
    }

    // =========================================================================
    // QUERY
    // =========================================================================

    public List<Match> getMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : allMatches)
            if (m.getRound() == round) result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    public List<Match> getWinnersMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : allMatches)
            if (m.getRound() == round && m != grandFinals && m.isWinnersBracket())
                result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    /** Returns only the WB matches inside the play-in sub-bracket for the given round. */
    public List<Match> getPlayInWinnersMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : playInMatches)
            if (m.getRound() == round && m.isWinnersBracket() && !m.isPlayInGrandFinal())
                result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    /** Returns LB matches inside the play-in sub-bracket for a given round. */
    public List<Match> getPlayInLosersMatchesByRound(int round) {
        List<Match> result = new ArrayList<>();
        for (Match m : playInMatches)
            if (m.getRound() == round && !m.isWinnersBracket() && !m.isPlayInGrandFinal())
                result.add(m);
        result.sort(Comparator.comparingInt(Match::getMatchId));
        return result;
    }

    public Match       getPlayInGrandFinal() {
        for (Match m : playInMatches) if (m.isPlayInGrandFinal()) return m;
        return null;
    }

    public List<Match> getAllMatches()            { return allMatches; }
    public List<Match> getLosersBracketMatches()  { return losersBracketMatches; }
    public List<Match> getPlayInMatches()         { return playInMatches; }
    public List<Match> getMainBracketMatches()    { return mainBracketMatches; }
    public Team[]      getByeTeams()              { return byeTeams; }
    public Match       getGrandFinals()           { return grandFinals; }
    public int         getTotalRounds()           { return totalRounds; }
    public Team[]      getTeams()                 { return teams; }
    public Match       getChampionship()          { return root; }
   
    public int         getPlayInWinnersRounds()   { return playInWinnersRounds; }
    public int         getWinnersRounds() {
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && teams.length == 24) return 5;
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && teams.length == 12) return 4;
        return (int) Math.ceil(Math.log(Math.max(teams.length, 2)) / Math.log(2));
    }

    public List<Match> getWinnersBracketMatches() {
        List<Match> r = new ArrayList<>();
        for (Match m : allMatches)
            if (m.isWinnersBracket() && m != grandFinals) r.add(m);
        return r;
    }

    public List<Match> getPendingMatches() {
        List<Match> r = new ArrayList<>();
        for (Match m : allMatches)
            if (!m.isCompleted() && m.getTeam1() != null && m.getTeam2() != null)
                r.add(m);
        return r;
    }

    public Match getCurrentMatch() {
        List<Match> p = getPendingMatches();
        return p.isEmpty() ? null : p.get(0);
    }

    public String getProgress() {
        int total = allMatches.size(), done = 0;
        for (Match m : allMatches) if (m.isCompleted()) done++;
        return done + "/" + total + " matches completed";
    }

    public boolean isPlayInFormat() {
        return tournamentType == TournamentType.PLAY_IN_DOUBLE_ELIMINATION
            || tournamentType == TournamentType.PLAY_IN_SINGLE_ELIMINATION;
    }

    public Team getTournamentWinner() {
        if (tournamentType == TournamentType.ROUND_ROBIN
                || tournamentType == TournamentType.SWISS
                || tournamentType == TournamentType.FREE_FOR_ALL) {
            // Only declare a winner when every match has been played
            for (Match m : allMatches) if (!m.isCompleted()) return null;
            if (allMatches.isEmpty()) return null;
            Team champ = null; int best = -1;
            for (Team t : teams) {
                if (t.getWins() > best) { best = t.getWins(); champ = t; }
                else if (t.getWins() == best && champ != null
                         && t.getPointDifference() > champ.getPointDifference())
                    champ = t;
            }
            return champ;
        }
        if (root == null || !root.isCompleted()) return null;
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION
                || tournamentType == TournamentType.PLAY_IN_DOUBLE_ELIMINATION
                || tournamentType == TournamentType.PLAY_IN_SINGLE_ELIMINATION) {
            if (grandFinals == null) return null;
            if (grandFinals.getTeam1() == null || grandFinals.getTeam2() == null) return null;
        }
        if (root.getTeam1() == null || root.getTeam2() == null) return null;
        return root.getWinner();
    }

    // =========================================================================
    // RECORD WINNER / PROPAGATION
    // =========================================================================

    public void recordWinner(Match match, Team winner, int score1, int score2) {
        if (match.getTeam1() == null || match.getTeam2() == null) {
            System.out.println("Error: Match missing a team!");
            return;
        }
        match.setWinner(winner, score1, score2);

        // Record scores in the matrix using team array index as ID
        int id1 = -1, id2 = -1;
        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == match.getTeam1()) id1 = i;
            if (teams[i] == match.getTeam2()) id2 = i;
        }
        if (id1 >= 0 && id2 >= 0) {
            int s1 = (match.getTeam1() == winner) ? score1 : score2;
            int s2 = (match.getTeam2() == winner) ? score1 : score2;
            scoreMatrix.recordMatch(id1, id2, s1, s2);
        }

        if (tournamentType == TournamentType.SINGLE_ELIMINATION
                || tournamentType == TournamentType.DOUBLE_ELIMINATION)
            propagateWinnerUp(match, winner);

        if (tournamentType == TournamentType.PLAY_IN_DOUBLE_ELIMINATION
                || tournamentType == TournamentType.PLAY_IN_SINGLE_ELIMINATION)
            propagatePlayIn(match, winner);

        System.out.println("✓ Recorded: " + match);
    }

    public ScoreMatrix getScoreMatrix() { return scoreMatrix; }

    public void revertMatch(Match match, String winnerId, String score) {
    if (winnerId == null) {
        // Match was incomplete in the snapshot — wipe its result
        match.clearResult();
    } else {
        // Match was completed — find the winner by name and restore it
        Team winner = null;
        for (Team t : teams) {
            if (t.getName().equals(winnerId)) { winner = t; break; }
        }
        if (winner != null) {
            match.forceSetResult(winner, score);
        }
    }
}

    private void propagateWinnerUp(Match currentMatch, Team winner) {
        Team loser = (currentMatch.getTeam1() == winner)
                     ? currentMatch.getTeam2() : currentMatch.getTeam1();

        // ── Feed loser into LB (WB matches only) ─────────────────────────────
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION && loser != null
                && currentMatch.getTeam1() != null && currentMatch.getTeam2() != null) {
            Match lbSlot = winnerToLoserMatch.get(currentMatch);
            if (lbSlot != null && !lbSlot.isCompleted()) {
                if (lbSlot.getTeam1() == null) {
                    lbSlot.setTeam1(loser);
                } else if (lbSlot.getTeam2() == null && lbSlot.getTeam1() != loser) {
                    lbSlot.setTeam2(loser);
                }
            }
        }

        // ── Advance winner via lbWinnerAdvanceMap (LB drop/elim rounds) ──────
        Match nextLb = lbWinnerAdvanceMap.get(currentMatch);
        if (nextLb != null && !nextLb.isCompleted()) {
            if (nextLb.getTeam2() == null) {
                nextLb.setTeam2(winner);
                System.out.println("→ LB Winner " + winner.getName() + " → match " + nextLb.getMatchId() + " (team2)");
            } else if (nextLb.getTeam1() == null) {
                nextLb.setTeam1(winner);
                System.out.println("→ LB Winner " + winner.getName() + " → match " + nextLb.getMatchId() + " (team1)");
            }
            return;
        }


        // ── Advance winner via child links (WB rounds + GF) ───────────────────
        for (Match m : allMatches) {
            if (m.getLeftChild() == currentMatch) {
                m.setTeam1(winner);
                if (m.isCompleted()) propagateWinnerUp(m, m.getWinner());
                return;
            } else if (m.getRightChild() == currentMatch) {
                m.setTeam2(winner);
                if (m.isCompleted()) propagateWinnerUp(m, m.getWinner());
                return;
            }
        }
    }

    /**
     * Propagation for Play-In DE format.
     *
     * Three paths:
     *  1. Play-in WB/LB match → loser goes to LB slot (same as standard DE)
     *  2. Play-in match winner advances to next play-in match via child refs
     *  3. Play-in survivor (GF winner or LB final winner) → fills a main bracket semifinal slot
     */
    private void propagatePlayIn(Match currentMatch, Team winner) {
        Team loser = (currentMatch.getTeam1() == winner)
                     ? currentMatch.getTeam2() : currentMatch.getTeam1();

        // Feed loser into play-in LB if applicable
        if (loser != null && currentMatch.isPlayIn() && !currentMatch.isPlayInGrandFinal()) {
            Match lbSlot = winnerToLoserMatch.get(currentMatch);
            if (lbSlot != null && !lbSlot.isCompleted()) {
                if (lbSlot.getTeam1() == null) {
                    lbSlot.setTeam1(loser);
                    System.out.println("→ PI Loser " + loser.getName() + " → LB R" + lbSlot.getRound());
                } else if (lbSlot.getTeam2() == null && lbSlot.getTeam1() != loser) {
                    lbSlot.setTeam2(loser);
                    System.out.println("→ PI Loser " + loser.getName() + " → LB R" + lbSlot.getRound());
                }
            }
        }

        // Advance winner to next play-in match via child links
        for (Match m : playInMatches) {
            if (m.getLeftChild() == currentMatch && m.getTeam1() == null) {
                m.setTeam1(winner);
                System.out.println("→ PI Winner " + winner.getName() + " → PI match " + m.getMatchId());
                if (m.isCompleted()) propagatePlayIn(m, m.getWinner());
                return;
            } else if (m.getRightChild() == currentMatch && m.getTeam2() == null) {
                m.setTeam2(winner);
                System.out.println("→ PI Winner " + winner.getName() + " → PI match " + m.getMatchId());
                if (m.isCompleted()) propagatePlayIn(m, m.getWinner());
                return;
            }
        }

        // If this match has a main bracket slot, send winner there
        Match mainSlot = currentMatch.getMainBracketSlot();
        if (mainSlot != null) {
            if (mainSlot.getTeam1() == null) {
                mainSlot.setTeam1(winner);
                System.out.println("→ PI Survivor " + winner.getName()
                    + " → Main match " + mainSlot.getMatchId() + " (team1)");
            } else if (mainSlot.getTeam2() == null) {
                mainSlot.setTeam2(winner);
                System.out.println("→ PI Survivor " + winner.getName()
                    + " → Main match " + mainSlot.getMatchId() + " (team2)");
            }
            // Once main bracket match has both teams, propagate inside main bracket
            propagateMainBracket(mainSlot);
            return;
        }

        // Advance winner inside main bracket via child links
        for (Match m : mainBracketMatches) {
            if (m.getLeftChild() == currentMatch) {
                m.setTeam1(winner);
                propagateMainBracket(m);
                return;
            } else if (m.getRightChild() == currentMatch) {
                m.setTeam2(winner);
                propagateMainBracket(m);
                return;
            }
        }
    }

    /** Propagates a completed main-bracket match forward to the next main-bracket match. */
    private void propagateMainBracket(Match current) {
        if (!current.isCompleted()) return;
        Team w = current.getWinner();
        for (Match m : mainBracketMatches) {
            if (m.getLeftChild() == current && m.getTeam1() == null) {
                m.setTeam1(w);
                propagateMainBracket(m);
                return;
            } else if (m.getRightChild() == current && m.getTeam2() == null) {
                m.setTeam2(w);
                propagateMainBracket(m);
                return;
            }
        }
    }

    // =========================================================================
    // DEBUG
    // =========================================================================

    public void printBracket() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🏆 TOURNAMENT - " + tournamentType.getDisplayName());
        System.out.println("=".repeat(60));
        if (tournamentType == TournamentType.DOUBLE_ELIMINATION) {
            int numRounds = (teams.length == 24) ? 5
                          : (int) Math.ceil(Math.log(teams.length) / Math.log(2));
            System.out.println("\nWINNERS BRACKET:");
            for (int r = 1; r <= numRounds; r++) {
                List<Match> ms = getWinnersMatchesByRound(r);
                if (!ms.isEmpty()) { System.out.println("  R" + r + ":"); for (Match m : ms) System.out.println("    " + m); }
            }
            System.out.println("\nLOSERS BRACKET:");
            for (Match m : losersBracketMatches) System.out.println("  R" + m.getRound() + ": " + m);
            if (grandFinals != null) System.out.println("\nGRAND FINAL:\n  " + grandFinals);
        } else if (tournamentType == TournamentType.PLAY_IN_DOUBLE_ELIMINATION
                || tournamentType == TournamentType.PLAY_IN_SINGLE_ELIMINATION) {
            String tag = tournamentType == TournamentType.PLAY_IN_SINGLE_ELIMINATION
                         ? "PLAY-IN BRACKET (SE)" : "PLAY-IN BRACKET (DE)";
            System.out.println("\n── " + tag + " ──");
            for (Match m : playInMatches) System.out.println("  R" + m.getRound() + ": " + m);
            System.out.println("\n── MAIN BRACKET ──");
            for (Match m : mainBracketMatches) System.out.println("  R" + m.getRound() + ": " + m);
        } else {
            for (int r = 1; r <= totalRounds; r++) {
                List<Match> ms = getMatchesByRound(r);
                if (!ms.isEmpty()) { System.out.println("  R" + r + ":"); for (Match m : ms) System.out.println("    " + m); }
            }
        }
    }

    public void printStandings() {
        System.out.println("\n📊 STANDINGS:");
        List<Team> sorted = new ArrayList<>(Arrays.asList(teams));
        sorted.sort((a, b) -> Integer.compare(b.getWins(), a.getWins()));
        for (int i = 0; i < sorted.size(); i++) {
            Team t = sorted.get(i);
            System.out.printf("%d. %-12s W:%d L:%d PD:%d Win%%:%.1f%%%n",
                    i+1, t.getName(), t.getWins(), t.getLosses(),
                    t.getPointDifference(), t.getWinPercentage());
        }
    }
}